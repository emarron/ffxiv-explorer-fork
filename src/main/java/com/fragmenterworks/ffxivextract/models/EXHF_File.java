package com.fragmenterworks.ffxivextract.models;

import com.fragmenterworks.ffxivextract.helpers.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class EXHF_File extends Game_File {

    public final static String[] languageCodes = {"", "_ja", "_en", "_de", "_fr", "_chs", "_cht", "_ko"};
    public final static String[] languageNames = {"", "Japanese", "English", "German", "French", "Chinese - Singapore", "Chinese - Traditional", "Korean"};

    private EXDF_Dataset[] datasetTable;
    private EXDF_Page[] pageTable;
    private int[] langTable;
    private int datasetChunkSize = 0;
    private int numEntries = 0;
    private int trueNumEntries = 0;

    public EXHF_File(byte[] data) throws IOException {
        super(ByteOrder.BIG_ENDIAN);
        loadEXHF(data);
    }

    public EXHF_File(String path) throws IOException {
        super(ByteOrder.BIG_ENDIAN);
        File file = new File(path);
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        loadEXHF(data);
    }

    private void loadEXHF(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(endian);

        try {
            int magic = buffer.getInt();
            int version = buffer.getShort();

            if (magic != 0x45584846 || version != 3) {
                Utils.getGlobalLogger().error("EXHF magic was incorrect.");
                Utils.getGlobalLogger().debug("Magic was {}", String.format("0x%08X", magic));
                return;
            }

            datasetChunkSize = buffer.getShort();
            int numDataSetTable = buffer.getShort();
            int numPageTable = buffer.getShort();
            int numLangTable = buffer.getShort();
            buffer.getShort();
            buffer.getShort();
            buffer.getShort();
            numEntries = buffer.getInt();
            buffer.getInt();
            buffer.position(0x20);

            datasetTable = new EXDF_Dataset[numDataSetTable];
            pageTable = new EXDF_Page[numPageTable];
            langTable = new int[numLangTable];

            //Dataset Table
            for (int i = 0; i < numDataSetTable; i++)
                datasetTable[i] = new EXDF_Dataset(buffer.getShort(), buffer.getShort());
			
			/*Arrays.sort(datasetTable, new Comparator<EXDF_Dataset>() {
				@Override
				public int compare(EXDF_Dataset a, EXDF_Dataset b) {
					return a.offset - b.offset;
				}
			});*/

            //Page Table
            for (int i = 0; i < numPageTable; i++) {
                pageTable[i] = new EXDF_Page(buffer.getInt(), buffer.getInt());
                trueNumEntries += pageTable[i].numEntries;
            }

            //Lang Table
            for (int i = 0; i < numLangTable; i++) {
                langTable[i] = buffer.get();
                buffer.get();
            }

        } catch (BufferUnderflowException | BufferOverflowException flowException) {
            Utils.getGlobalLogger().error(flowException);
        }
    }

    public static class EXDF_Dataset {
        public final short type;
        public final short offset;

        EXDF_Dataset(short type, short offset) {
            this.type = type;
            this.offset = offset;
        }
    }

    public static class EXDF_Page {
        public final int pageNum;
        public final int numEntries;

        EXDF_Page(int pageNum, int numEntries) {
            this.pageNum = pageNum;
            this.numEntries = numEntries;
        }
    }

    public int getNumPages() {
        return pageTable.length;
    }

    public int getNumLanguages() {
        return langTable.length;
    }

    public EXDF_Page[] getPageTable() {
        return pageTable;
    }

    public int[] getLanguageTable() {
        return langTable;
    }

    public int getTrueNumEntries() {
        return trueNumEntries;
    }

    public int getNumEntries() {
        return numEntries;
    }

    public EXDF_Dataset[] getDatasetTable() {
        return datasetTable;
    }

    public int getDatasetChunkSize() {
        return datasetChunkSize;
    }
}
