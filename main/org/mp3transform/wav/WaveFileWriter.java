/*
 * 11/19/04 1.0 moved to LGPL.
 * 02/23/99 JavaConversion by E.B
 * Don Cross, April 1993.
 * RIFF file format classes.
 * See Chapter 8 of "Multimedia Programmer's Reference" in
 * the Microsoft Windows SDK.
 *  
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */
package org.mp3transform.wav;

import java.io.IOException;
import java.io.RandomAccessFile;

public class WaveFileWriter {
    static class WaveFormatChunkData {
        short formatTag = 0; // Format category (PCM=1)
        short channels = 0; // Number of channels (mono=1, stereo=2)
        int samplesPerSec = 0; // Sampling rate [Hz]
        int avgBytesPerSec = 0;
        short blockAlign = 0;
        short bitsPerSample = 0;

        WaveFormatChunkData() {
            formatTag = 1; // PCM
            config(44100, (short) 16, (short) 1);
        }

        void config(int samplingRate, short bitsPerSample,
                short channels) {
            samplesPerSec = samplingRate;
            this.channels = channels;
            this.bitsPerSample = bitsPerSample;
            avgBytesPerSec = (channels * samplesPerSec * bitsPerSample) / 8;
            blockAlign = (short) ((channels * bitsPerSample) / 8);
        }
    }

    static class WaveFormatChunk {
        RiffChunkHeader header;
        WaveFormatChunkData data;

        WaveFormatChunk() {
            header = new RiffChunkHeader();
            data = new WaveFormatChunkData();
            header.ckID = fourCC("fmt ");
            header.ckSize = 16;
        }
    }

    static class RiffChunkHeader {
        int ckID = 0; // Four-character chunk ID
        int ckSize = 0; // Length of data in chunk
    }

    private final RiffChunkHeader riffHeader = new RiffChunkHeader();
    private final RandomAccessFile file; // I/O stream to use
    private final WaveFormatChunk waveFormat = new WaveFormatChunk();
    private final RiffChunkHeader pcmData = new RiffChunkHeader();
    private long pcmDataOffset = 0; // offset of 'pcmData' in output file

    WaveFileWriter(String fileName, int samplingRate, short bitsPerSample,
            short channels) throws IOException {
        riffHeader.ckID = fourCC("RIFF");
        pcmData.ckID = fourCC("data");
        if (bitsPerSample != 8 && bitsPerSample != 16) {
            throw new IOException("Unsupported bitsPerSample: " + bitsPerSample);
        }
        if (channels < 1 || channels > 2) {
            throw new IOException("Unsupported channels: " + channels);
        }
        waveFormat.data.config(samplingRate, bitsPerSample, channels);
        file = new RandomAccessFile(fileName, "rw");
        // Write the RIFF header...
        // We will have to come back later and patch it!
        byte[] br = new byte[8];
        br[0] = (byte) ((riffHeader.ckID >>> 24) & 0x000000FF);
        br[1] = (byte) ((riffHeader.ckID >>> 16) & 0x000000FF);
        br[2] = (byte) ((riffHeader.ckID >>> 8) & 0x000000FF);
        br[3] = (byte) (riffHeader.ckID & 0x000000FF);
        int ckSize = riffHeader.ckSize;
        byte br4 = (byte) ((ckSize >>> 24) & 0x000000FF);
        byte br5 = (byte) ((ckSize >>> 16) & 0x000000FF);
        byte br6 = (byte) ((ckSize >>> 8) & 0x000000FF);
        byte br7 = (byte) (ckSize & 0x000000FF);
        br[4] = br7;
        br[5] = br6;
        br[6] = br5;
        br[7] = br4;
        file.write(br, 0, 8);
        // write the wav header
        byte[] theWave = { (byte) 'W', (byte) 'A', (byte) 'V', (byte) 'E' };
        write(theWave, 4);
        write(waveFormat.header, 8);
        writeShort(waveFormat.data.formatTag);
        writeShort(waveFormat.data.channels);
        writeInt(waveFormat.data.samplesPerSec);
        writeInt(waveFormat.data.avgBytesPerSec);
        writeShort(waveFormat.data.blockAlign);
        writeShort(waveFormat.data.bitsPerSample);
        pcmDataOffset = file.getFilePointer();
        write(pcmData, 8);
    }

    private void writeShort(short data) throws IOException {
        short theData = (short) (((data >>> 8) & 0x00FF) | ((data << 8) & 0xFF00));
        file.writeShort(theData);
        riffHeader.ckSize += 2;
    }

    void writeData(short[] data, int numData) throws IOException {
        int extraBytes = numData * 2;
        pcmData.ckSize += extraBytes;
        writeRiff(data, extraBytes);
    }

    private void writeRiff(short[] data, int numBytes) throws IOException {
        byte[] theData = new byte[numBytes];
        int yc = 0;
        for (int y = 0; y < numBytes; y = y + 2) {
            theData[y] = (byte) (data[yc] & 0x00FF);
            theData[y + 1] = (byte) ((data[yc++] >>> 8) & 0x00FF);
        }
        file.write(theData, 0, numBytes);
        riffHeader.ckSize += numBytes;
    }

    private void writeInt(int data) throws IOException {
        short theDataL = (short) ((data >>> 16) & 0x0000FFFF);
        short theDataR = (short) (data & 0x0000FFFF);
        short theDataLI = (short) (((theDataL >>> 8) & 0x00FF) | ((theDataL << 8) & 0xFF00));
        short theDataRI = (short) (((theDataR >>> 8) & 0x00FF) | ((theDataR << 8) & 0xFF00));
        int theData = ((theDataRI << 16) & 0xFFFF0000)
                | (theDataLI & 0x0000FFFF);
        file.writeInt(theData);
        riffHeader.ckSize += 4;
    }

    private void write(RiffChunkHeader header, int numBytes) throws IOException {
        byte[] br = new byte[8];
        br[0] = (byte) ((header.ckID >>> 24) & 0x000000FF);
        br[1] = (byte) ((header.ckID >>> 16) & 0x000000FF);
        br[2] = (byte) ((header.ckID >>> 8) & 0x000000FF);
        br[3] = (byte) (header.ckID & 0x000000FF);
        byte br4 = (byte) ((header.ckSize >>> 24) & 0x000000FF);
        byte br5 = (byte) ((header.ckSize >>> 16) & 0x000000FF);
        byte br6 = (byte) ((header.ckSize >>> 8) & 0x000000FF);
        byte br7 = (byte) (header.ckSize & 0x000000FF);
        br[4] = br7;
        br[5] = br6;
        br[6] = br5;
        br[7] = br4;
        file.write(br, 0, numBytes);
        riffHeader.ckSize += numBytes;
    }

    void close() throws IOException {
        backpatch(pcmDataOffset, pcmData, 8);
        file.seek(0);
        byte[] br = new byte[8];
        br[0] = (byte) ((riffHeader.ckID >>> 24) & 0x000000FF);
        br[1] = (byte) ((riffHeader.ckID >>> 16) & 0x000000FF);
        br[2] = (byte) ((riffHeader.ckID >>> 8) & 0x000000FF);
        br[3] = (byte) (riffHeader.ckID & 0x000000FF);
        br[7] = (byte) ((riffHeader.ckSize >>> 24) & 0x000000FF);
        br[6] = (byte) ((riffHeader.ckSize >>> 16) & 0x000000FF);
        br[5] = (byte) ((riffHeader.ckSize >>> 8) & 0x000000FF);
        br[4] = (byte) (riffHeader.ckSize & 0x000000FF);
        file.write(br, 0, 8);
        file.close();
    }

    private void backpatch(long fileOffset, RiffChunkHeader data, int numBytes)
            throws IOException {
        file.seek(fileOffset);
        write(data, numBytes);
    }

    private void write(byte[] data, int numBytes) throws IOException {
        file.write(data, 0, numBytes);
        riffHeader.ckSize += numBytes;
    }

    static int fourCC(String chunkName) {
        byte[] p = chunkName.getBytes();
        int ret = (((p[0] << 24) & 0xFF000000) | ((p[1] << 16) & 0x00FF0000)
                | ((p[2] << 8) & 0x0000FF00) | (p[3] & 0x000000FF));
        return ret;
    }
}
