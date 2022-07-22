package com.fragmenterworks.ffxivextract.gui;

import com.fragmenterworks.ffxivextract.Constants;
import com.fragmenterworks.ffxivextract.Strings;
import com.fragmenterworks.ffxivextract.gui.SearchWindow.ISearchComplete;
import com.fragmenterworks.ffxivextract.gui.components.*;
import com.fragmenterworks.ffxivextract.gui.modelviewer.ModelViewerWindow;
import com.fragmenterworks.ffxivextract.gui.outfitter.OutfitterWindow;
import com.fragmenterworks.ffxivextract.helpers.*;
import com.fragmenterworks.ffxivextract.models.*;
import com.fragmenterworks.ffxivextract.models.SCD_File.SCD_Sound_Info;
import com.fragmenterworks.ffxivextract.models.SqPack_IndexFile.SqPack_File;
import com.fragmenterworks.ffxivextract.storage.HashDatabase;
import unluac.decompile.Decompiler;
import unluac.decompile.OutputProvider;
import unluac.parse.BHeader;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.prefs.Preferences;

@SuppressWarnings("serial")
public class FileManagerWindow extends JFrame implements TreeSelectionListener, ISearchComplete, WindowListener {

    private final JMenuBar menu = new JMenuBar();

    //FILE IO
    private File lastOpenedIndexFile = null;
    private File lastSaveLocation = null;
    private SqPack_IndexFile currentIndexFile;

    //UI
    private SearchWindow searchWindow;
    private final ExplorerPanel_View fileTree = new ExplorerPanel_View();
    private final JSplitPane splitPane;
    private final JLabel lblOffsetValue;
    private final JLabel lblHashValue;
    private final JLabel lblContentTypeValue;
    private final JLabel lblHashInfoValue;
    private final Hex_View hexView = new Hex_View(16);
    private EXDF_View exhfComponent;
    private final JProgressBar prgLoadingBar;
    private final JLabel lblLoadingBarString;
    private TexturePaint paint;
    private final JScrollPane defaultScrollPane;
    private final JViewport defaultViewPort;

    //MENU
    private JMenuItem file_Extract;
    private JMenuItem file_ExtractRaw;
    private JMenuItem file_Close;
    private JMenuItem search_search;
    private JMenuItem search_searchAgain;
    private JCheckBoxMenuItem options_enableUpdate;
    private JCheckBoxMenuItem options_showAsHex;
    private JCheckBoxMenuItem options_sortByOffset;

    public FileManagerWindow(String title) {
        addWindowListener(this);

        //Load generic bg img
        BufferedImage bg;
        try {
            bg = ImageIO.read(getClass().getResource("/triangular.png"));
            paint = new TexturePaint(bg, new Rectangle(120, 120));
        } catch (IOException e) {
            Utils.getGlobalLogger().error(e);
        }

        setupMenu();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(1200, 800);
        this.setTitle(title);

        URL imageURL = getClass().getResource("/frameicon.png");
        ImageIcon image = new ImageIcon(imageURL);
        this.setIconImage(image.getImage());

        JPanel pnlContent = new JPanel();

        pnlContent.registerKeyboardAction(menuHandler, "search", KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        pnlContent.registerKeyboardAction(menuHandler, "extractc", KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_MASK), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        pnlContent.registerKeyboardAction(menuHandler, "extractr", KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.SHIFT_MASK | KeyEvent.CTRL_MASK), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        pnlContent.registerKeyboardAction(menuHandler, "searchagain", KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        getContentPane().add(pnlContent, BorderLayout.CENTER);
        pnlContent.setLayout(new BoxLayout(pnlContent, BoxLayout.X_AXIS));

        defaultScrollPane = new JScrollPane();
        defaultViewPort = new JViewport() {
            @Override
            public boolean isOpaque() {
                return false;
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setPaint(paint);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        defaultScrollPane.setViewport(defaultViewPort);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                fileTree, defaultScrollPane);
        pnlContent.add(splitPane);

        splitPane.setDividerLocation(150);

        fileTree.addTreeSelectionListener(this);

        JPanel pnlStatusBar = new JPanel();
        getContentPane().add(pnlStatusBar, BorderLayout.SOUTH);
        pnlStatusBar.setLayout(new BorderLayout(0, 0));

        JSeparator separator = new JSeparator();
        pnlStatusBar.add(separator, BorderLayout.NORTH);

        JPanel pnlInfo = new JPanel();
        FlowLayout fl_pnlInfo = (FlowLayout) pnlInfo.getLayout();
        fl_pnlInfo.setVgap(4);
        pnlInfo.setBorder(null);
        pnlStatusBar.add(pnlInfo, BorderLayout.WEST);

        JLabel lblOffset = new JLabel("Offset: ");
        pnlInfo.add(lblOffset);
        lblOffset.setHorizontalAlignment(SwingConstants.LEFT);

        lblOffsetValue = new JLabel("*");
        pnlInfo.add(lblOffsetValue);

        JSeparator separator_1 = new JSeparator();
        separator_1.setPreferredSize(new Dimension(1, 16));
        separator_1.setOrientation(SwingConstants.VERTICAL);
        pnlInfo.add(separator_1);

        JLabel lblHash = new JLabel("Hash: ");
        pnlInfo.add(lblHash);

        lblHashValue = new JLabel("*");
        pnlInfo.add(lblHashValue);

        JSeparator separator_2 = new JSeparator();
        separator_2.setPreferredSize(new Dimension(1, 16));
        separator_2.setOrientation(SwingConstants.VERTICAL);
        pnlInfo.add(separator_2);

        JLabel lblContentType = new JLabel("Content Type: ");
        pnlInfo.add(lblContentType);

        lblContentTypeValue = new JLabel("*");
        pnlInfo.add(lblContentTypeValue);

        JSeparator separator_3 = new JSeparator();
        separator_3.setPreferredSize(new Dimension(1, 16));
        separator_3.setOrientation(SwingConstants.VERTICAL);
        pnlInfo.add(separator_3);

        lblHashInfoValue = new JLabel("* / *");
        pnlInfo.add(lblHashInfoValue);

        JLabel lblHashInfo = new JLabel(" filenames loaded");
        pnlInfo.add(lblHashInfo);

        JPanel pnlProgBar = new JPanel();
        pnlStatusBar.add(pnlProgBar, BorderLayout.EAST);

        prgLoadingBar = new JProgressBar();
        prgLoadingBar.setVisible(false);

        lblLoadingBarString = new JLabel("0%");
        lblLoadingBarString.setHorizontalAlignment(SwingConstants.RIGHT);
        lblLoadingBarString.setVisible(false);
        pnlProgBar.add(lblLoadingBarString);
        pnlProgBar.add(prgLoadingBar);

        setLocationRelativeTo(null);

        Preferences prefs = Preferences.userNodeForPackage(com.fragmenterworks.ffxivextract.Main.class);

        if (prefs.get(Constants.PREF_LASTOPENED, null) != null)
            lastOpenedIndexFile = new File(prefs.get(Constants.PREF_LASTOPENED, null));

        HavokNative.initHavokNativ();
    }

    private void openFile(File selectedFile) {

        if (currentIndexFile != null) {
            if (splitPane.getRightComponent() instanceof JTabbedPane) {
                JTabbedPane tabs = (JTabbedPane) splitPane.getRightComponent();

                if (tabs.getTabCount() > 0) {
                    if (tabs.getComponentAt(0) instanceof Sound_View) {
                        ((Sound_View) tabs.getComponentAt(0)).stopPlayback();
                    }
                }
            }
            closeFile();
        }

        OpenIndexTask openTask = new OpenIndexTask(selectedFile);
        openTask.execute();
    }

    private void closeFile() {

        if (currentIndexFile == null)
            return;

        fileTree.fileClosed();
        currentIndexFile = null;

        setTitle(Constants.APPNAME);
        hexView.setBytes(null);
        splitPane.setRightComponent(defaultScrollPane);
        file_Close.setEnabled(false);
        search_search.setEnabled(false);
        search_searchAgain.setEnabled(false);

        lblOffsetValue.setText("*");
        lblHashValue.setText("*");
        lblContentTypeValue.setText("*");
    }

    private final ActionListener menuHandler = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent event) {
            if (event.getActionCommand().equals("open")) {
                JFileChooser fileChooser = new JFileChooser(lastOpenedIndexFile);
                FileFilter filter = new FileFilter() {

                    @Override
                    public String getDescription() {
                        return Strings.FILETYPE_FFXIV_INDEX;
                    }

                    @Override
                    public boolean accept(File f) {
                        return f.getName().endsWith(".index") || f.isDirectory();
                    }
                };
                FileFilter filter2 = new FileFilter() {

                    @Override
                    public String getDescription() {
                        return Strings.FILETYPE_FFXIV_INDEX2;
                    }

                    @Override
                    public boolean accept(File f) {
                        return f.getName().endsWith(".index2") || f.isDirectory();
                    }
                };
                fileChooser.addChoosableFileFilter(filter);
                fileChooser.addChoosableFileFilter(filter2);

                fileChooser.setFileFilter(filter);
                fileChooser.setAcceptAllFileFilterUsed(false);
                int retunval = fileChooser.showOpenDialog(FileManagerWindow.this);
                if (retunval == JFileChooser.APPROVE_OPTION) {
                    lastOpenedIndexFile = fileChooser.getSelectedFile();
                    openFile(fileChooser.getSelectedFile());

                    Preferences prefs = Preferences.userNodeForPackage(com.fragmenterworks.ffxivextract.Main.class);
                    prefs.put(Constants.PREF_LASTOPENED, lastOpenedIndexFile.getAbsolutePath());
                }
            } else if (event.getActionCommand().equals("close")) {
                closeFile();
            } else if (event.getActionCommand().equals("extractc") && file_Extract.isEnabled()) {
                extract(true);
            } else if (event.getActionCommand().equals("extractr") && file_ExtractRaw.isEnabled()) {
                extract(false);
            } else if (event.getActionCommand().equals("search") && search_search.isEnabled()) {
                searchWindow.setLocationRelativeTo(FileManagerWindow.this);
                searchWindow.setVisible(true);
                searchWindow.reset();
            } else if (event.getActionCommand().equals("searchagain") && search_searchAgain.isEnabled()) {
                searchWindow.searchAgain();
            } else if (event.getActionCommand().equals("hashcalc")) {
                Path_to_Hash_Window hasher = new Path_to_Hash_Window(currentIndexFile);
                hasher.setLocationRelativeTo(FileManagerWindow.this);
                hasher.setVisible(true);
            } else if (event.getActionCommand().equals("modelviewer")) {
                if (Constants.datPath == null || Constants.datPath.isEmpty() || !new File(Constants.datPath).exists()) {
                    JOptionPane.showMessageDialog(
                            FileManagerWindow.this,
                            "You have not set a valid FFXIV path. Please set it first in Settings under the Options menu.",
                            "FFXIV Path Not Set",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                ModelViewerWindow modelviewer = new ModelViewerWindow(FileManagerWindow.this, Constants.datPath);
                //modelviewer.setLocationRelativeTo(FileManagerWindow.this);
                modelviewer.beginLoad();
            } else if (event.getActionCommand().equals("outfitter")) {
                if (Constants.datPath == null || Constants.datPath.isEmpty() || !new File(Constants.datPath).exists()) {
                    JOptionPane.showMessageDialog(
                            FileManagerWindow.this,
                            "You have not set a valid FFXIV path. Please set it first in Settings under the Options menu.",
                            "FFXIV Path Not Set",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                OutfitterWindow outfitter = new OutfitterWindow(FileManagerWindow.this, Constants.datPath);
                //modelviewer.setLocationRelativeTo(FileManagerWindow.this);
                outfitter.beginLoad();
            } else if (event.getActionCommand().equals("musicswapper")) {
                MusicSwapperWindow swapper = new MusicSwapperWindow();
                swapper.setLocationRelativeTo(FileManagerWindow.this);
                swapper.setVisible(true);
            } else if (event.getActionCommand().equals("macroeditor")) {
                MacroEditorWindow macroEditor = new MacroEditorWindow();
                macroEditor.setLocationRelativeTo(FileManagerWindow.this);
                macroEditor.setVisible(true);
            } else if (event.getActionCommand().equals("logviewer")) {
                LogViewerWindow logViewer = new LogViewerWindow();
                logViewer.setLocationRelativeTo(FileManagerWindow.this);
                logViewer.setVisible(true);
            } else if (event.getActionCommand().equals("find_exh")) {
                if (Constants.datPath == null || Constants.datPath.isEmpty() || !new File(Constants.datPath).exists()) {
                    JOptionPane.showMessageDialog(
                            FileManagerWindow.this,
                            "You have not set a valid FFXIV path. Please set it first in Settings under the Options menu.",
                            "FFXIV Path Not Set",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                HashFinding_Utils.findExhHashes();

                JOptionPane.showMessageDialog(
                        FileManagerWindow.this,
                        "Finished searching for hashes. Reopen the archive if you have it currently open.",
                        "Done",
                        JOptionPane.INFORMATION_MESSAGE);
            } else if (event.getActionCommand().equals("find_music")) {
                if (Constants.datPath == null || Constants.datPath.isEmpty() || !new File(Constants.datPath).exists()) {
                    JOptionPane.showMessageDialog(
                            FileManagerWindow.this,
                            "You have not set a valid FFXIV path. Please set it first in Settings under the Options menu.",
                            "FFXIV Path Not Set",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                HashFinding_Utils.findMusicHashes();

                JOptionPane.showMessageDialog(
                        FileManagerWindow.this,
                        "Finished searching for hashes. Reopen the archive if you have it currently open.",
                        "Done",
                        JOptionPane.INFORMATION_MESSAGE);
            } else if (event.getActionCommand().equals("find_maps")) {
                if (Constants.datPath == null || Constants.datPath.isEmpty() || !new File(Constants.datPath).exists()) {
                    JOptionPane.showMessageDialog(
                            FileManagerWindow.this,
                            "You have not set a valid FFXIV path. Please set it first in Settings under the Options menu.",
                            "FFXIV Path Not Set",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                HashFinding_Utils.findMapHashes();

                JOptionPane.showMessageDialog(
                        FileManagerWindow.this,
                        "Finished searching for hashes. Reopen the archive if you have it currently open.",
                        "Done",
                        JOptionPane.INFORMATION_MESSAGE);
            } else if (event.getActionCommand().equals("settings")) {
                SettingsWindow settings = new SettingsWindow(FileManagerWindow.this);
                settings.setLocationRelativeTo(FileManagerWindow.this);
                settings.setVisible(true);
            } else if (event.getActionCommand().equals("options_update")) {
                Preferences prefs = Preferences.userNodeForPackage(com.fragmenterworks.ffxivextract.Main.class);
                prefs.putBoolean(Constants.PREF_DO_DB_UPDATE, options_enableUpdate.isSelected());
            } else if (event.getActionCommand().equals("quit")) {
                System.exit(0);
            } else if (event.getActionCommand().equals("about")) {
                AboutWindow aboutWindow = new AboutWindow(FileManagerWindow.this);
                aboutWindow.setLocationRelativeTo(FileManagerWindow.this);
                aboutWindow.setVisible(true);
            } else if (event.getActionCommand().equals("cedumpimport")) {
                JFileChooser fc = new JFileChooser();
                int returnVal = fc.showOpenDialog(getParent()); //Where frame is the parent component
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    int added  = HashDatabase.importFilePaths(fc.getSelectedFile());
                    if (added >= 0)
                        Utils.getGlobalLogger().info("Added {} new paths to db.", added);
                    else
                        Utils.getGlobalLogger().error("Added {} new paths before an error occurred.", added * -1);
                }
            } else if (event.getActionCommand().equals("dbimport")) {
                JFileChooser fc = new JFileChooser();
                FileFilter filter = new FileNameExtensionFilter("FFXIV Explorer database", "db");
                fc.setFileFilter(filter);
                int returnVal = fc.showOpenDialog(getParent()); //Where frame is the parent component
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    HashDatabase.importDatabase(fc.getSelectedFile());
                }
            } else if (event.getActionCommand().equals("fileinject")) {
                FileInjectorWindow swapper = new FileInjectorWindow();
                swapper.setLocationRelativeTo(FileManagerWindow.this);
                swapper.setVisible(true);
            }
        }
    };

    private void setupMenu() {

        //File Menu
        JMenu file = new JMenu(Strings.MENU_FILE);
        JMenu search = new JMenu(Strings.MENU_SEARCH);
        JMenu dataviewers = new JMenu(Strings.MENU_DATAVIEWERS);
        JMenu tools = new JMenu(Strings.MENU_TOOLS);
        JMenu database = new JMenu(Strings.MENU_DATABASE);
        JMenu options = new JMenu(Strings.MENU_OPTIONS);
        JMenu help = new JMenu(Strings.MENU_HELP);

        JMenuItem file_Open = new JMenuItem(Strings.MENUITEM_OPEN);
        file_Open.setActionCommand("open");

        file_Close = new JMenuItem(Strings.MENUITEM_CLOSE);
        file_Close.setEnabled(false);
        file_Close.setActionCommand("close");

        file_Extract = new JMenuItem(Strings.MENUITEM_EXTRACT);
        file_Extract.setEnabled(false);
        file_Extract.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_MASK));

        file_ExtractRaw = new JMenuItem(Strings.MENUITEM_EXTRACTRAW);
        file_ExtractRaw.setEnabled(false);
        file_ExtractRaw.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.SHIFT_MASK | KeyEvent.CTRL_MASK));
        file_Extract.setActionCommand("extractc");
        file_ExtractRaw.setActionCommand("extractr");

        JMenuItem file_Quit = new JMenuItem(Strings.MENUITEM_QUIT);
        file_Quit.setActionCommand("quit");
        file_Open.addActionListener(menuHandler);
        file_Close.addActionListener(menuHandler);
        file_Extract.addActionListener(menuHandler);
        file_ExtractRaw.addActionListener(menuHandler);
        file_Quit.addActionListener(menuHandler);

        search_search = new JMenuItem(Strings.MENUITEM_SEARCH);
        search_search.setEnabled(false);
        search_search.setActionCommand("search");
        search_search.addActionListener(menuHandler);
        search_search.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK));

        search_searchAgain = new JMenuItem(Strings.MENUITEM_SEARCHAGAIN);
        search_searchAgain.setEnabled(false);
        search_searchAgain.setActionCommand("searchagain");
        search_searchAgain.addActionListener(menuHandler);
        search_searchAgain.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));

        JMenuItem dataviewer_modelViewer = new JMenuItem(Strings.MENUITEM_MODELVIEWER);
        dataviewer_modelViewer.setActionCommand("modelviewer");
        dataviewer_modelViewer.addActionListener(menuHandler);

        JMenuItem dataviewer_outfitter = new JMenuItem(Strings.MENUITEM_OUTFITTER);
        dataviewer_outfitter.setActionCommand("outfitter");
        dataviewer_outfitter.addActionListener(menuHandler);

        JMenuItem tools_musicswapper = new JMenuItem(Strings.MENUITEM_MUSICSWAPPER);
        tools_musicswapper.setActionCommand("musicswapper");
        tools_musicswapper.addActionListener(menuHandler);

        JMenuItem db_hashcalculator = new JMenuItem(Strings.MENUITEM_HASHCALC);
        db_hashcalculator.setActionCommand("hashcalc");
        db_hashcalculator.addActionListener(menuHandler);

        JMenuItem tools_macroEditor = new JMenuItem(Strings.MENUITEM_MACROEDITOR);
        tools_macroEditor.setActionCommand("macroeditor");
        tools_macroEditor.addActionListener(menuHandler);

        JMenuItem tools_logViewer = new JMenuItem(Strings.MENUITEM_LOGVIEWER);
        tools_logViewer.setActionCommand("logviewer");
        tools_logViewer.addActionListener(menuHandler);

        JMenuItem tools_findexhs = new JMenuItem(Strings.MENUITEM_FIND_EXH);
        tools_findexhs.setActionCommand("find_exh");
        tools_findexhs.addActionListener(menuHandler);

        JMenuItem tools_findmusic = new JMenuItem(Strings.MENUITEM_FIND_MUSIC);
        tools_findmusic.setActionCommand("find_music");
        tools_findmusic.addActionListener(menuHandler);

        JMenuItem tools_findmaps = new JMenuItem(Strings.MENUITEM_FIND_MAPS);
        tools_findmaps.setActionCommand("find_maps");
        tools_findmaps.addActionListener(menuHandler);

        JMenuItem options_settings = new JMenuItem(Strings.MENUITEM_SETTINGS);
        options_settings.setActionCommand("settings");
        options_settings.addActionListener(menuHandler);

        options_showAsHex = new JCheckBoxMenuItem(Strings.MENUITEM_EXD_HEX_OPTION, false);
        options_showAsHex.setActionCommand("options_showAsHex");

        options_sortByOffset = new JCheckBoxMenuItem(Strings.MENUITEM_EXD_OFFSET_OPTION, false);
        options_sortByOffset.setActionCommand("options_sortByOffset");

        Preferences prefs = Preferences.userNodeForPackage(com.fragmenterworks.ffxivextract.Main.class);
        options_enableUpdate = new JCheckBoxMenuItem(Strings.MENUITEM_ENABLEUPDATE, prefs.getBoolean(Constants.PREF_DO_DB_UPDATE, false));
        options_enableUpdate.setActionCommand("options_update");
        options_enableUpdate.addActionListener(menuHandler);

        JMenuItem help_About = new JMenuItem("About");

        help_About.setActionCommand("about");
        help_About.addActionListener(menuHandler);

        JMenuItem db_importcedump = new JMenuItem(Strings.MENUITEM_CEDUMPIMPORT);
        db_importcedump.setActionCommand("cedumpimport");
        db_importcedump.addActionListener(menuHandler);

        JMenuItem db_importdb = new JMenuItem(Strings.MENUITEM_DBIMPORT);
        db_importdb.setActionCommand("dbimport");
        db_importdb.addActionListener(menuHandler);


        JMenuItem tools_fileinject = new JMenuItem(Strings.MENUITEM_FILEINJECT);
        tools_fileinject.setActionCommand("fileinject");
        tools_fileinject.addActionListener(menuHandler);

        file.add(file_Open);
        file.add(file_Close);
        file.addSeparator();
        file.add(file_Extract);
        file.add(file_ExtractRaw);
        file.addSeparator();
        file.add(file_Quit);

        search.add(search_search);
        search.add(search_searchAgain);

        dataviewers.add(dataviewer_modelViewer);
        dataviewers.add(dataviewer_outfitter);

        tools.add(tools_musicswapper);
        tools.add(tools_fileinject);
        tools.add(tools_macroEditor);
        tools.add(tools_logViewer);
        tools.addSeparator();
        tools.add(tools_findexhs);
        tools.add(tools_findmusic);
        tools.add(tools_findmaps);

        database.add(db_hashcalculator);
        database.add(db_importcedump);
        database.add(db_importdb);

        options.add(options_settings);
        options.add(options_enableUpdate);
        options.add(options_showAsHex);
        options.add(options_sortByOffset);

        help.add(help_About);

        //Super Menus
        menu.add(file);
        menu.add(search);
        menu.add(dataviewers);
        menu.add(tools);
        menu.add(database);
        menu.add(options);
        menu.add(help);

        this.setJMenuBar(menu);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {

        if (fileTree.isOnlyFolder()) {
            splitPane.setRightComponent(defaultScrollPane);
            lblOffsetValue.setText("*");
            lblHashValue.setText("*");
            lblContentTypeValue.setText("*");
            file_Extract.setEnabled(true);
            file_ExtractRaw.setEnabled(true);
            return;
        }

        if (fileTree.getSelectedFiles().size() == 0) {
            file_Extract.setEnabled(false);
            file_ExtractRaw.setEnabled(false);
            return;
        } else {
            file_Extract.setEnabled(true);
            file_ExtractRaw.setEnabled(true);
        }

        if (fileTree.getSelectedFiles().size() > 1) {
            lblOffsetValue.setText("*");
            lblHashValue.setText("*");
            lblContentTypeValue.setText("*");
        } else {
            int datNum = currentIndexFile.getDatNum(fileTree.getSelectedFiles().get(0).getOffset());
            long realOffset = currentIndexFile.getOffsetInBytes(fileTree.getSelectedFiles().get(0).getOffset());

            lblOffsetValue.setText(String.format("0x%08X", realOffset) + " (Dat: " + datNum + ")");
            lblHashValue.setText(String.format("0x%08X", fileTree.getSelectedFiles().get(0).getId()));

            try {
                lblContentTypeValue.setText("" + currentIndexFile.getContentType(fileTree.getSelectedFiles().get(0).getOffset()));
            } catch (IOException ioe) {
                lblContentTypeValue.setText("Content Type Error");
            }
        }

        if (splitPane.getRightComponent() instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) splitPane.getRightComponent();

            if (tabs.getTabCount() > 0) {
                if (tabs.getComponentAt(0) instanceof Sound_View) {
                    ((Sound_View) tabs.getComponentAt(0)).stopPlayback();
                }
            }
        }

        openData(fileTree.getSelectedFiles().get(0));
    }

    private void openData(SqPack_File file) {

        JTabbedPane tabs = new JTabbedPane();

        byte[] data = null;
        int contentType = -1;
        try {
            contentType = currentIndexFile.getContentType(file.getOffset());

            //If it's a placeholder, don't bother
            if (contentType == 0x01) {
                JLabel lblFNFError = new JLabel("This is currently a placeholder, there is no data here.");
                tabs.addTab("No Data", lblFNFError);
                hexView.setBytes(null);
                splitPane.setRightComponent(tabs);
                return;
            }

            data = currentIndexFile.extractFile(file.dataoffset, null);
        } catch (FileNotFoundException eFNF) {
            Utils.getGlobalLogger().error("Could not find dat {} for {}", currentIndexFile.getDatNum(file.getOffset()), file.getName(), eFNF);
            JLabel lblFNFError = new JLabel("The dat for this file is missing!");
            tabs.addTab("Extract Error", lblFNFError);
            hexView.setBytes(null);
            splitPane.setRightComponent(tabs);
            return;
        } catch (IOException e) {
            Utils.getGlobalLogger().error("Could not extract file {}", file.getName(), e);
            JLabel lblLoadError = new JLabel("Something went terribly wrong extracting this file.");
            tabs.addTab("Extract Error", lblLoadError);
            hexView.setBytes(null);
            splitPane.setRightComponent(tabs);
            return;
        }

        //disable opengl for BE packs
        boolean threedee = !currentIndexFile.isBigEndian();

        if (contentType == 3 && threedee) {
            OpenGL_View view = null;

            try {
                if (HashDatabase.getFolder(file.getId2()) == null)
                    view = new OpenGL_View(new Model(null, currentIndexFile, data, currentIndexFile.getEndian()));
                else
                    view = new OpenGL_View(new Model(HashDatabase.getFolder(file.getId2()) + "/" + file.getName(), currentIndexFile, data, currentIndexFile.getEndian()));
            } catch (Exception modelException) {
                modelException.printStackTrace();
                JLabel lblLoadError = new JLabel("Error loading Model.");
                tabs.addTab("Error", lblLoadError);
                hexView.setBytes(data);
                tabs.addTab("Raw Hex", hexView);
                splitPane.setRightComponent(tabs);
                return;
            }

            tabs.addTab("3D Model", view);
        }

        if (data == null) {
            JLabel lblLoadError = new JLabel("Something went terribly wrong extracting this file.");
            tabs.addTab("Extract Error", lblLoadError);
            hexView.setBytes(null);
            splitPane.setRightComponent(tabs);
            return;
        }

        //TOOD: refactor this to use byte magic
        if (data.length >= 3 && checkMagic(data, "EXDF")) {
            if (exhfComponent == null || !exhfComponent.isSame(file.getName()))
                exhfComponent = new EXDF_View(currentIndexFile, HashDatabase.getFolder(file.getId2()) + "/" + file.getName(), options_showAsHex.getState(), options_sortByOffset.getState());
            tabs.addTab("EXDF File", exhfComponent);
        } else if (data.length >= 3 && checkMagic(data, "EXHF")) {
            try {
                if (exhfComponent == null || !exhfComponent.isSame(file.getName()))
                    exhfComponent = new EXDF_View(currentIndexFile, HashDatabase.getFolder(file.getId2()) + "/" + file.getName(), new EXHF_File(data), options_showAsHex.getState(), options_sortByOffset.getState());
                tabs.addTab("EXHF File", exhfComponent);
            } catch (IOException e) {
                Utils.getGlobalLogger().error(e);
            }
        } else if (data.length >= 8 && checkMagic(data, "SEDBSSCF")) {
            Sound_View scdComponent;
            try {
                scdComponent = new Sound_View(new SCD_File(data, currentIndexFile.getEndian()));
                tabs.addTab("SCD File", scdComponent);
            } catch (IOException e) {
                Utils.getGlobalLogger().error(e);
            }
        } else if (data.length >= 4 && checkMagic(data, "XFVA")) {
            AVFX_File avfxFile = new AVFX_File(data, currentIndexFile.getEndian());
//			avfxFile.printOut();
        } else if (contentType == 4 || file.getName().endsWith("atex")) {
            Image_View imageComponent = new Image_View(new Texture_File(data, currentIndexFile.getEndian()));
            tabs.addTab("TEX File", imageComponent);
        } else if (data.length >= 5 && checkMagic(data, "LuaQ", 1)) { //TODO: double-check this if you ever feel like working on SDAT
            try {
                Lua_View luaView;

                String text = getDecompiledLuaString(data);

                luaView = new Lua_View(("-- Decompiled using unluac_2015_06_13 2.0.1 by tehtmi (https://sourceforge.net/projects/unluac/)\n" + text).split("\\r?\\n"));
                tabs.addTab("Decompiled Lua", luaView);
            } catch (Exception e) {
                Utils.getGlobalLogger().error(e);
            }
        } else if (data.length >= 4 && checkMagic(data, "pap ")) {
            try {
                PAP_View papView = new PAP_View(new PAP_File(data, currentIndexFile.getEndian()));
                tabs.addTab("Animation File", papView);
            } catch (IOException e) {
                Utils.getGlobalLogger().error(e);
            }
        } else if (data.length >= 4 && checkMagic(data, "ShCd")) {
//            try {
//                Shader_View shaderView = new Shader_View(new SHCD_File(data, currentIndexFile.getEndian()));
//                tabs.addTab("Shader File", shaderView);
//            } catch (IOException e) {
//                Utils.getGlobalLogger().error(e);
//            }
        } else if (data.length >= 4 && checkMagic(data, "ShPk")) {
//            try {
//                Shader_View shaderView = new Shader_View(new SHPK_File(data, currentIndexFile.getEndian()));
//                tabs.addTab("Shader Pack", shaderView);
//            } catch (IOException e) {
//                Utils.getGlobalLogger().error(e);
//            }
        } else if (data.length >= 4 && checkMagic(data, "SGB1")) {
            try {
                SGB_File sgbFile = new SGB_File(data, currentIndexFile.getEndian());
            } catch (IOException e) {
                Utils.getGlobalLogger().error(e);
            }
        } else if (data.length >= 4 && checkMagic(data, "uldh")) {
//            ULD_View uldView = new ULD_View(new ULD_File(data, currentIndexFile.getEndian()));
//            tabs.addTab("ULD Renderer", uldView);
        } else if (file.getName().equals("human.cmp")) {
            CMP_File cmpFile = new CMP_File(data);
            CMP_View cmpView = new CMP_View(cmpFile);
            tabs.addTab("CMP Viewer", cmpView);
        }

        hexView.setBytes(data);
        //if (contentType != 3)
        tabs.addTab("Raw Hex", hexView);
        splitPane.setRightComponent(tabs);
    }

    private boolean checkMagic(byte[] data, String magic) {
        return checkMagic(data, magic, 0);
    }

    private boolean checkMagic(byte[] data, String magic, int startOffset) {
        byte[] littleBuf = new byte[magic.length()];
        byte[] bigBuf = new byte[magic.length()];

        System.arraycopy(data, startOffset, littleBuf, 0, magic.length());
        System.arraycopy(data, startOffset, bigBuf, 0, magic.length());

        // Didn't want to take the chance that an alternating pattern, i.e. big, little, big, little
        // would somehow happen to match a magic
        boolean matchesLittle = true;
        boolean matchesBig = true;

        for (int i = 0; i < magic.length(); i++) {
            if (littleBuf[i] != magic.charAt(i)) {
                matchesLittle = false;
                break;
            }
        }

        if (matchesLittle)
            return true;

        for (int i = magic.length() - 1; i >= 0; i--) {
            if (bigBuf[i] != magic.charAt(magic.length() - 1 - i)) {
                matchesBig = false;
                break;
            }
        }

        return matchesBig;
    }

    private void extract(boolean doConvert) {
        JFileChooser fileChooser = new JFileChooser(lastSaveLocation);

        ArrayList<SqPack_File> files = fileTree.getSelectedFiles();


        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        FileFilter filter = new FileFilter() {

            @Override
            public String getDescription() {
                return "FFXIV Converted";
            }

            @Override
            public boolean accept(File f) {
                return f.getName().endsWith(".csv") || f.getName().endsWith(".ogg") || f.getName().endsWith(".wav") || f.getName().endsWith(".tga") || f.getName().endsWith(".hkx") || f.getName().endsWith(".obj") || f.isDirectory();
            }
        };
        fileChooser.addChoosableFileFilter(filter);
        fileChooser.setFileFilter(filter);
        fileChooser.setAcceptAllFileFilterUsed(false);


        int retunval = fileChooser.showSaveDialog(FileManagerWindow.this);

        if (retunval == JFileChooser.APPROVE_OPTION) {
            lastSaveLocation = fileChooser.getSelectedFile();
            lastSaveLocation.getParentFile().mkdirs();

            Loading_Dialog loadingDialog = new Loading_Dialog(FileManagerWindow.this, files.size());
            loadingDialog.setTitle("Extracting...");
            ExtractTask task = new ExtractTask(files, loadingDialog, doConvert);
            task.execute();
            loadingDialog.setLocationRelativeTo(this);
            loadingDialog.setVisible(true);
        }
    }

    private String getDecompiledLuaString(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();

        BHeader header = new BHeader(buffer, new unluac.Configuration());
        Decompiler d = new Decompiler(header.main);
        Decompiler.State t = d.decompile();
        final StringBuilder pout = new StringBuilder();

        d.print(t, new OutputProvider() {
            @Override
            public void print(String s) {
                pout.append(s);
            }

            @Override
            public void print(byte b) {
                pout.append(b);
            }

            @Override
            public void println() {
                pout.append("\n");
            }
        });
        return pout.toString();
    }

    private String getExtension(int contentType, byte[] data) {
        if (data.length >= 4 && data[0] == 'E' && data[1] == 'X' && data[2] == 'D' && data[3] == 'F')
            return ".exd";
        else if (data.length >= 4 && data[0] == 'E' && data[1] == 'X' && data[2] == 'H' && data[3] == 'F')
            return ".exh";
        else if (data.length >= 5 && data[1] == 'L' && data[2] == 'u' && data[3] == 'a' && data[4] == 'Q')
            return ".luab";
        else if (data.length >= 4 && data[0] == 'S' && data[1] == 'E' && data[2] == 'D' && data[3] == 'B')
            return ".scd";
        else if (data.length >= 4 && data[0] == 'p' && data[1] == 'a' && data[2] == 'p' && data[3] == ' ')
            return ".hkx";
        else if (data.length >= 4 && data[0] == 'b' && data[1] == 'l' && data[2] == 'k' && data[3] == 's')
            return ".hkx";
        else if (data.length >= 4 && data[0] == 'S' && data[1] == 'h' && data[2] == 'C' && data[3] == 'd')
            return ".cso";
        else if (data.length >= 4 && data[0] == 'S' && data[1] == 'h' && data[2] == 'P' && data[3] == 'k')
            return ".shpk";
        else if (contentType == 3) {
            return ".obj";
        } else if (contentType == 4) {
            return ".tga";
        } else
            return "";
    }

    class OpenIndexTask extends SwingWorker<Void, Void> {

        final File selectedFile;

        OpenIndexTask(File selectedFile) {
            this.selectedFile = selectedFile;
            menu.setEnabled(false);
            prgLoadingBar.setVisible(true);
            prgLoadingBar.setValue(0);
            lblLoadingBarString.setVisible(true);
            for (int i = 0; i < menu.getMenuCount(); i++)
                menu.getMenu(i).setEnabled(false);
        }

        @Override
        protected Void doInBackground() {
            try {
                HashDatabase.beginConnection();
                currentIndexFile = new SqPack_IndexFile(selectedFile.getAbsolutePath(), prgLoadingBar, lblLoadingBarString);
                HashDatabase.closeConnection();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(FileManagerWindow.this,
                        "There was an error opening this index file.",
                        "File Open Error",
                        JOptionPane.ERROR_MESSAGE);
                Utils.getGlobalLogger().error(e);
                return null;
            }
            return null;
        }

        @Override
        protected void done() {
            Utils.getGlobalLogger().trace("{}", currentIndexFile);

            setTitle(Constants.APPNAME + " [" + selectedFile.getName() + "]");
            file_Close.setEnabled(true);
            search_search.setEnabled(true);
            prgLoadingBar.setValue(0);
            prgLoadingBar.setVisible(false);
            lblLoadingBarString.setVisible(false);
            fileTree.fileOpened(currentIndexFile);
            searchWindow = new SearchWindow(FileManagerWindow.this, currentIndexFile, FileManagerWindow.this);
            for (int i = 0; i < menu.getMenuCount(); i++)
                menu.getMenu(i).setEnabled(true);
            lblHashInfoValue.setText(String.format("%d / %d", currentIndexFile.getNumUnhashedFiles(), currentIndexFile.getTotalFiles()));
        }
    }

    class ExtractTask extends SwingWorker<Void, Void> {

        final ArrayList<SqPack_File> files;
        final Loading_Dialog loadingDialog;
        final boolean doConvert;

        ExtractTask(ArrayList<SqPack_File> files, Loading_Dialog loadingDialog, boolean doConvert) {
            this.files = files;
            this.loadingDialog = loadingDialog;
            this.doConvert = doConvert;
        }

        @Override
        protected Void doInBackground() throws Exception {
            EXDF_View tempView = null;

            for (int i = 0; i < files.size(); i++) {
                try {
                    String folderName = HashDatabase.getFolder(files.get(i).getId2());
                    String fileName = files.get(i).getName();
                    if (fileName == null)
                        fileName = String.format("%X", files.get(i).getId());
                    if (folderName == null)
                        folderName = String.format("%X", files.get(i).getId2());

                    loadingDialog.nextFile(i, folderName + "/" + fileName);

                    if (currentIndexFile.getContentType(files.get(i).getOffset()) == 1)
                        continue;

                    byte[] data = currentIndexFile.extractFile(files.get(i).getOffset(), loadingDialog);
                    byte[] dataToSave = null;

                    if (data == null)
                        continue;

                    int contentType = currentIndexFile.getContentType(files.get(i).getOffset());
                    String extension = getExtension(contentType, data);

                    if (doConvert) {
                        if (extension.equals(".exh")) {
                            if (tempView != null && tempView.isSame(files.get(i).getName()))
                                continue;
                            EXHF_File file = new EXHF_File(data);

                            tempView = new EXDF_View(currentIndexFile,
                                    HashDatabase.getFolder(fileTree.getSelectedFiles().get(i).getId2()) + "/" + fileTree.getSelectedFiles().get(i).getName(),
                                    file,
                                    options_showAsHex.getState(),
                                    options_sortByOffset.getState());

                            for (int l = 0; l < (tempView.getNumLangs()); l++) {

                                String path = lastSaveLocation.getCanonicalPath();

                                if (fileName == null)
                                    fileName = String.format("%X", files.get(i).getId());

                                if (folderName == null)
                                    folderName = String.format("%X", files.get(i).getId2());

                                path = lastSaveLocation.getCanonicalPath() + "\\" + folderName + "\\" + fileName;

                                File mkDirPath = new File(path);
                                mkDirPath.getParentFile().mkdirs();

                                tempView.saveCSV(path + EXHF_File.languageCodes[tempView.getExhFile().getLanguageTable()[l]] + ".csv", l);

                            }

                            continue;
                        } else if (extension.equals(".exd")) {
                            if (tempView != null && tempView.isSame(files.get(i).getName()))
                                continue;

                            tempView = new EXDF_View(currentIndexFile,
                                    HashDatabase.getFolder(fileTree.getSelectedFiles().get(i).getId2()) + "/" + fileTree.getSelectedFiles().get(i).getName(),
                                    options_showAsHex.getState(),
                                    options_sortByOffset.getState());

                            //Remove the thing
                            String exhName = files.get(i).getName();

                            for (int l = 0; l < EXHF_File.languageCodes.length; l++)
                                exhName = exhName.replace(String.format("%s.exd", EXHF_File.languageCodes[l]), "");

                            exhName = exhName.substring(0, exhName.lastIndexOf("_")) + ".exh";

                            for (int l = 0; l < (tempView.getNumLangs()); l++) {
                                String path = lastSaveLocation.getCanonicalPath();

                                fileName = exhName;

                                if (folderName == null)
                                    folderName = String.format("%X", files.get(i).getId2());

                                path = lastSaveLocation.getCanonicalPath() + "\\" + folderName + "\\" + fileName;

                                File mkDirPath = new File(path);
                                mkDirPath.getParentFile().mkdirs();

                                tempView.saveCSV(path + EXHF_File.languageCodes[tempView.getExhFile().getLanguageTable()[l]] + ".csv", l);
                            }

                            continue;
                        } else if (extension.equals(".obj")) {
                            Model model = new Model(folderName + "/" + fileName, currentIndexFile, data, currentIndexFile.getEndian());

                            String path = lastSaveLocation.getCanonicalPath();

                            if (fileName == null)
                                fileName = String.format("%X", files.get(i).getId());

                            if (folderName == null)
                                folderName = String.format("%X", files.get(i).getId2());

                            path = lastSaveLocation.getCanonicalPath() + "\\" + folderName + "\\" + fileName;

                            File mkDirPath = new File(path);
                            mkDirPath.getParentFile().mkdirs();

                            WavefrontObjectWriter.writeObj(path, model, currentIndexFile.getEndian());

                            continue;
                        } else if (extension.equals(".hkx")) {
                            if (data.length >= 4 && data[0] == 'p' && data[1] == 'a' && data[2] == 'p' && data[3] == ' ') {
                                PAP_File pap = new PAP_File(data, currentIndexFile.getEndian());
                                dataToSave = pap.getHavokData();
                            } else if (data.length >= 4 && data[0] == 'b' && data[1] == 'l' && data[2] == 'k' && data[3] == 's') {
                                SKLB_File pap = new SKLB_File(data, currentIndexFile.getEndian());
                                dataToSave = pap.getHavokData();
                            }

                            extension = ".hkx";
                        } else if (extension.equals(".cso")) {
                            SHCD_File shader = new SHCD_File(data, currentIndexFile.getEndian());
                            dataToSave = shader.getShaderBytecode();
                            extension = ".cso";
                        } else if (extension.equals(".shpk") && doConvert) {
                            try {
                                SHPK_File shader = new SHPK_File(data, currentIndexFile.getEndian());

                                for (int j = 0; j < shader.getNumVertShaders(); j++) {
                                    dataToSave = shader.getShaderBytecode(j);
                                    extension = ".vs.cso";
                                    String path = lastSaveLocation.getCanonicalPath();

                                    if (fileName == null)
                                        fileName = String.format("%X", files.get(i).getId());

                                    if (folderName == null)
                                        folderName = String.format("%X", files.get(i).getId2());

                                    path = lastSaveLocation.getCanonicalPath() + "\\" + folderName + "\\" + fileName;

                                    File mkDirPath = new File(path);
                                    mkDirPath.getParentFile().mkdirs();

                                    EARandomAccessFile out = new EARandomAccessFile(path + j + extension, "rw", ByteOrder.LITTLE_ENDIAN);
                                    out.write(dataToSave, 0, dataToSave.length);
                                    out.close();
                                }
                                for (int j = 0; j < shader.getNumPixelShaders(); j++) {
                                    dataToSave = shader.getShaderBytecode(shader.getNumVertShaders() + j);
                                    extension = ".ps.cso";
                                    String path = lastSaveLocation.getCanonicalPath();

                                    if (fileName == null)
                                        fileName = String.format("%X", files.get(i).getId());

                                    if (folderName == null)
                                        folderName = String.format("%X", files.get(i).getId2());

                                    path = lastSaveLocation.getCanonicalPath() + "\\" + folderName + "\\" + fileName;

                                    File mkDirPath = new File(path);
                                    mkDirPath.getParentFile().mkdirs();

                                    EARandomAccessFile out = new EARandomAccessFile(path + j + extension, "rw", ByteOrder.LITTLE_ENDIAN);
                                    out.write(dataToSave, 0, dataToSave.length);
                                    out.close();
                                }

                            } catch (IOException e) {
                                Utils.getGlobalLogger().error(e);
                            }
                        } else if (extension.equals(".tga")) {
                            Texture_File tex = new Texture_File(data, currentIndexFile.getEndian());
                            dataToSave = tex.getImage("tga");
                            extension = ".tga";
                        } else if (extension.equals(".luab")) {
                            String text = getDecompiledLuaString(data);
                            dataToSave = text.getBytes();
                        } else if (extension.equals(".scd")) {
                            SCD_File file = new SCD_File(data, currentIndexFile.getEndian());

                            for (int s = 0; s < file.getNumEntries(); s++) {
                                SCD_Sound_Info info = file.getSoundInfo(s);
                                if (info == null)
                                    continue;
                                if (info.dataType == 0x06) {
                                    dataToSave = file.getConverted(s);
                                    extension = ".ogg";
                                } else if (info.dataType == 0x0C) {
                                    dataToSave = file.getConverted(s);
                                    extension = ".wav";
                                } else
                                    continue;

                                String path = lastSaveLocation.getCanonicalPath();

                                if (fileName == null)
                                    fileName = String.format("%X", files.get(i).getId());

                                if (folderName == null)
                                    folderName = String.format("%X", files.get(i).getId2());

                                path = lastSaveLocation.getCanonicalPath() + "\\" + folderName + "\\" + fileName;

                                File mkDirPath = new File(path);
                                mkDirPath.getParentFile().mkdirs();

                                EARandomAccessFile out = new EARandomAccessFile(path + (file.getNumEntries() == 1 ? "" : "_" + s) + extension, "rw", ByteOrder.LITTLE_ENDIAN);
                                out.write(dataToSave, 0, dataToSave.length);
                                out.close();
                            }
                            continue;
                        }
                    } else {
                        dataToSave = data;
                    }

                    if (dataToSave == null) {
                        JOptionPane.showMessageDialog(FileManagerWindow.this,
                                String.format("%X", files.get(i).getId()) + " could not be converted to " + extension.substring(1).toUpperCase() + ".",
                                "Export Error",
                                JOptionPane.ERROR_MESSAGE);
                        continue;
                    }

                    String path = lastSaveLocation.getCanonicalPath();

                    if (fileName == null)
                        fileName = String.format("%X", files.get(i).getId());

                    if (folderName == null)
                        folderName = String.format("%X", files.get(i).getId2());

                    if (!doConvert)
                        extension = "";

                    path = lastSaveLocation.getCanonicalPath() + "\\" + folderName + "\\" + fileName.substring(0,fileName.length()-4);
                    if (currentIndexFile.isBigEndian()) {
                        int period = path.lastIndexOf(".");
                        String ext = path.substring(period);
                        path = path.replace(ext, ".ps3.d" + ext);
                    }

                    File mkDirPath = new File(path);
                    mkDirPath.getParentFile().mkdirs();

                    EARandomAccessFile out = new EARandomAccessFile(path + extension, "rw", ByteOrder.LITTLE_ENDIAN);
                    out.write(dataToSave, 0, dataToSave.length);
                    out.close();
                } catch (FileNotFoundException e) {
                    Utils.getGlobalLogger().error(e);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void done() {
            loadingDialog.setVisible(false);
            loadingDialog.dispose();
        }

    }


    @Override
    public void onSearchChosen(SqPack_File file) {

        if (file == null) {
            search_searchAgain.setEnabled(false);
            searchWindow.reset();
            return;
        }

        if (splitPane.getRightComponent() instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) splitPane.getRightComponent();

            if (tabs.getTabCount() > 0) {
                if (tabs.getComponentAt(0) instanceof Sound_View) {
                    ((Sound_View) tabs.getComponentAt(0)).stopPlayback();
                }
            }
        }

        openData(file);
        fileTree.select(file.getOffset());
        search_searchAgain.setEnabled(true);

    }

    @Override
    public void windowActivated(WindowEvent arg0) {
    }

    @Override
    public void windowClosed(WindowEvent arg0) {
    }

    @Override
    public void windowClosing(WindowEvent arg0) {
        closeFile();
    }

    @Override
    public void windowDeactivated(WindowEvent arg0) {
    }

    @Override
    public void windowDeiconified(WindowEvent arg0) {
    }

    @Override
    public void windowIconified(WindowEvent arg0) {
    }

    @Override
    public void windowOpened(WindowEvent arg0) {
    }


}
