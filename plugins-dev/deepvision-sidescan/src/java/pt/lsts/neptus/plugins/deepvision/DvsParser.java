/*
 * Copyright (c) 2004-2023 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Pedro Costa
 * 04/Oct/2023
 */
package pt.lsts.neptus.plugins.deepvision;

import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.mp.SystemPositionAndAttitude;
import pt.lsts.neptus.mra.api.SidescanLine;
import pt.lsts.neptus.mra.api.SidescanParameters;
import pt.lsts.neptus.mra.api.SidescanUtil;
import pt.lsts.neptus.types.coord.LocationType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author: Pedro Costa
 */
public class DvsParser {
    File file;
    DvsHeader dvsHeader;
    // List of the Ping Pos data
    ArrayList<DvsPos> posDataList;
    // List of the Ping Return data
    ArrayList<DvsReturn> returnDataList;


    public DvsParser(File file) {
        this.file = file;
        dvsHeader = new DvsHeader();
        posDataList = new ArrayList<>();
        returnDataList = new ArrayList<>();

        readInData();
    }

    // Called by constructor
    private void readInData() {
        int filePosition = 0;

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            ByteBuffer buffer;
            FileChannel fileChannel = fileInputStream.getChannel();
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, filePosition, dvsHeader.HEADER_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Header
            int VERSION = buffer.getInt() & 0xFFFFFFFF; // Turn int to unsigned int value
            float sampleRes = buffer.getFloat();
            float lineRate = buffer.getFloat();
            int nSamples = buffer.getInt();
            boolean left = buffer.get() > 0;
            boolean right = buffer.get() > 0;

            if (!dvsHeader.versionMatches(VERSION)) {
                NeptusLog.pub().error("Dvs file is not version 1. Abort.");
                return;
            }
            dvsHeader.setSampleResolution(sampleRes);
            dvsHeader.setLineRate(lineRate);
            dvsHeader.setnSamples(nSamples);
            dvsHeader.setLeftChannelActive(left);
            dvsHeader.setRightChannelActive(right);

            filePosition += dvsHeader.HEADER_SIZE;

            // Pos + Return
            int bufferSize = dvsHeader.getNumberOfActiveChannels() * dvsHeader.getnSamples() + DvsPos.SIZE;
            while (filePosition < file.length()) {
                buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, filePosition, bufferSize);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                // Ping Pos
                DvsPos dvsPos = new DvsPos();
                dvsPos.setLatitude(buffer.getDouble());
                dvsPos.setLongitude(buffer.getDouble());
                dvsPos.setSpeed(buffer.getFloat());
                dvsPos.setHeading(buffer.getFloat());
                dvsPos.setTimestamp((long) (posDataList.size() / (dvsHeader.getLineRate() / 1000)));
                posDataList.add(dvsPos);

                byte[] dst = new byte[dvsHeader.getnSamples() * dvsHeader.getNumberOfActiveChannels()];
                buffer.get(dst);

                // Bytes from the left channel array need to be reversed
                if (dvsHeader.isLeftChannelActive()) {
                    int length = dvsHeader.getnSamples();
                    reverseArray(dst, length);
                }
                returnDataList.add(new DvsReturn(dst));

                filePosition += bufferSize;
            }

            fileChannel.close();
        }
        catch (FileNotFoundException e) {
            NeptusLog.pub().error("File " + file.getAbsolutePath() + " not found while creating the DvsParser object.");
            e.printStackTrace();
        }
        catch (IOException e) {
            NeptusLog.pub().error("While trying to read " + file.getAbsolutePath() + " an IOException occurred");
            e.printStackTrace();
        }

    }


    public long getLastPingTimestamp() {
        return posDataList.get(posDataList.size() - 1).getTimestamp();
    }

    public long getFirstPingTimestamp() {
        return 0L;
    }

    public ArrayList<Integer> getSubsystemList() {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(0);
        return list;
    }

    public ArrayList<SidescanLine> getLinesBetween(long timestamp1, long timestamp2, int subsystem, SidescanParameters params) {
        DvsPos dvsPos;
        DvsReturn dvsReturn;
        long timestamp;
        float range;
        SystemPositionAndAttitude state;
        float frequency;
        double[] data;

        ArrayList<SidescanLine> lines = new ArrayList<>();
        int index = findTimestampIndex(timestamp1);

        while(index < posDataList.size()) {
            dvsPos = posDataList.get(index);
            timestamp = dvsPos.getTimestamp();

            if (timestamp >= timestamp2) {
                break;
            }

            dvsReturn = returnDataList.get(index);
            range = dvsHeader.getSampleResolution();
            state = new SystemPositionAndAttitude();
            state.setPosition(new LocationType(dvsPos.getLatitudeDegrees(), dvsPos.getLongitudeDegrees()));
            frequency = dvsHeader.getLineRate();
            data = dvsReturn.getData();
            data = SidescanUtil.applyNormalizationAndTVG(data, range, params);

            SidescanLine line = new SidescanLine(timestamp, range, state, frequency, data);
            lines.add(line);

            index++;
        }

        return lines;
    }

    public void cleanup() {
    }

    private int findTimestampIndex(long timestamp) {
        // Binary search the timestamps
        int left = 0;
        int right = posDataList.size() - 1;

        if (timestamp < posDataList.get(0).getTimestamp() || timestamp > posDataList.get(right).getTimestamp()) {
            return -1;
        }

        while (left < right) {
            int middle = left + (right - left) / 2;
            long middleTimestamp = posDataList.get(middle).getTimestamp();

            if (middleTimestamp == timestamp) {
                return middle;
            }

            if (timestamp > middleTimestamp) {
                left = middle + 1;
            }
            else {
                right = middle - 1;
            }
        }

        return left; // Return closest index
    }

    /**
     * Reverse an array in place
     *
     * @param arr the byte array to reverse
     * @param index reverse from start up to (not including) index
     */
    private void reverseArray(byte[] arr, int index) {
        byte temp;

        for (int i = 0; i < index / 2; i++) {
            temp = arr[i];
            arr[i] = arr[index - 1 - i];
            arr[index - 1 - i] = temp;
        }
    }
}


