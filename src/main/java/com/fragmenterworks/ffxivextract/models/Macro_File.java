package com.fragmenterworks.ffxivextract.models;

import com.fragmenterworks.ffxivextract.helpers.EARandomAccessFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class Macro_File {

    public final static int MAX_MACROS = 100;
    public final static int MAX_LINES = 15;
    private final static int XOR_BYTE = 0x73;
    public static final int MAX_NAME_LENGTH = 20;
    public final static int MAX_BODY_LENGTH = 0xB5;

    public final Macro_Entry[] entries = new Macro_Entry[MAX_MACROS];

    public Macro_File(String path) throws IOException {
        EARandomAccessFile ref = new EARandomAccessFile(path, "rw", ByteOrder.LITTLE_ENDIAN);

        ref.skipBytes(0x08);
        int macroBookSize = ref.readInt();
        ref.seek(0x10);

        //Read the data in
        byte[] macrobookData = new byte[macroBookSize];
        ref.readFully(macrobookData);

        ref.close();

        //XOR it
        for (int i = 0; i < macrobookData.length; i++)
            macrobookData[i] ^= XOR_BYTE;

        //Read in macro entries
        ByteBuffer bb = ByteBuffer.wrap(macrobookData);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.position(1);
        for (int i = 0; i < MAX_MACROS; i++) {
            entries[i] = readMacroEntry(bb);
        }
    }

    private Macro_Entry readMacroEntry(ByteBuffer bb) throws IOException {

        byte[] buffer = new byte[MAX_BODY_LENGTH];
        String title, icon;
        int iconInt;
        String[] lines = new String[MAX_LINES];

        if (bb.get() != 'T') //something wrong
            throw new IOException("Didn't find T marker where it should be");

        //TITLE
        int titleSize = bb.getShort();
        bb.get(buffer, 0, titleSize);
        title = new String(buffer, 0, titleSize - 1, StandardCharsets.UTF_8);

        if (bb.get() != 'I') //something wrong
            throw new IOException("Didn't find I marker where it should be");

        //ICON
        int iconSize = bb.getShort();
        bb.get(buffer, 0, iconSize);
        icon = new String(buffer, 0, iconSize, StandardCharsets.UTF_8);
        iconInt = Integer.parseInt(icon.trim(), 16);

        //K?
        if (bb.get() != 'K') //something wrong
            throw new IOException("Didn't find K marker where it should be");

        int kSize = bb.getShort();
        bb.get(buffer, 0, kSize);

        //LINES
        for (int i = 0; i < MAX_LINES; i++) {

            if (bb.get() != 'L') //something wrong
                throw new IOException("Didn't find L marker where it should be");

            int lineSize = bb.getShort();
            bb.get(buffer, 0, lineSize);
            lines[i] = new String(buffer, 0, lineSize - 1, StandardCharsets.UTF_8);
        }
        return new Macro_Entry(title, iconInt, lines);
    }

    public static class Macro_Entry {
        public String title;
        public int icon;
        public final String[] lines;

        Macro_Entry(String title, int icon, String[] lines) {
            this.title = title;
            this.icon = icon;
            this.lines = lines;
        }
    }
}
