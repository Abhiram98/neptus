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
 * Author: Pedro Gonçalves
 */
package pt.lsts.neptus.plugins.videostream;

import org.apache.commons.io.FileUtils;
import org.opencv.core.Size;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.util.FileUtil;
import pt.lsts.neptus.util.conf.ConfigFetch;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author pedrog
 * @author Pedro Costa
 * @version 1.0
 * @category OpenCV-Vision
 */
public class UtilVideoStream {
    private UtilVideoStream() {
    }

    public static String getHostFromURI(String camUrl) {
        try {
            URI uri = new URI(camUrl);
            if (uri == null) return null;

            return uri.getHost();
        }
        catch (Exception e) {
            NeptusLog.pub().warn("Camera URL is not valid: " + camUrl + " :: " + e.getMessage());
        }
        return null;
    }

    public static URI getCamUrlAsURI(String camUrl) {
        try {
            URI uri = new URI(camUrl);
            return uri;
        }
        catch (Exception e) {
            NeptusLog.pub().warn("Camera URL is not valid: " + camUrl + " :: " + e.getMessage());
        }
        return null;
    }

    public static ArrayList<Camera> readIpUrl(File nameFile) {
        ArrayList<Camera> cameraList = new ArrayList<>();
        BufferedReader br = null;
        String line;
        try {
            br = new BufferedReader(new FileReader(nameFile));
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        cameraList.add(new Camera());

        String[] splits;
        try {
            while ((line = br.readLine()) != null) {
                Camera cam = parseLineCamera(line);
                if (cam != null) {
                    cameraList.add(cam);
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        try {
            br.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return cameraList;
    }

    static Camera parseLineCamera(String line) {
        if (line.isEmpty() || line.trim().startsWith("#")) return null;

        String[] splits = line.split("#");
        if (splits.length == 3) {
            if (splits[0].trim().isEmpty()) return null;
            if (splits[1].trim().isEmpty()) return null;
            if (splits[2].trim().isEmpty()) return null;
            if (UtilVideoStream.getHostFromURI(splits[2].trim().trim()) == null) return null;

            return new Camera(splits[0], splits[1], splits[2]);
        }
        else if (splits.length == 2) {
            if (splits[0].trim().isEmpty()) return null;
            if (splits[1].trim().isEmpty()) return null;
            String host = UtilVideoStream.getHostFromURI(splits[1].trim().trim());
            if (host == null) return null;

            return new Camera(splits[0], host, splits[1]);
        }

        return null;
    }

    public static void removeCamFromFile(Camera camToRemove, String fileName) {
        File confIni = new File(fileName);
        File tempFile = null;
        try {
            tempFile = File.createTempFile("neptus_", "tmp", new File(ConfigFetch.getNeptusTmpDir()));
            tempFile.deleteOnExit();
        }
        catch (IOException e) {
            e.printStackTrace();
            return;
        }

        String currentLine;
        try (BufferedReader reader = new BufferedReader(new FileReader(confIni));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            while ((currentLine = reader.readLine()) != null) {
                boolean writeLine = false;

                if (currentLine.isEmpty() || currentLine.trim().startsWith("#")) {
                    writeLine = true;
                }

                if (writeLine == false) {
                    Camera cam = parseLineCamera(currentLine.trim());
                    if (cam != null) {
                        if (!cam.getName().equalsIgnoreCase(camToRemove.getName())) {
                            writeLine = true;
                        }
                    }
                }

                if (writeLine) {
                    writer.write(currentLine.trim() + System.getProperty("line.separator"));
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        try {
            FileUtils.copyFile(tempFile, confIni);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addCamToFile(Camera camToAdd, String fileName) {
        String iniRsrcPath = FileUtil.getResourceAsFileKeepName(VideoStream.BASE_FOLDER_FOR_URL_INI);

        File confIni = new File(fileName);
        if (!confIni.exists()) {
            FileUtil.copyFileToDir(iniRsrcPath, ConfigFetch.getConfFolder());
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("neptus_", "tmp", new File(ConfigFetch.getNeptusTmpDir()));
            tempFile.deleteOnExit();
        }
        catch (IOException e) {
            e.printStackTrace();
            return;
        }

        ArrayList<Camera> camsList = readIpUrl(confIni);
        boolean updateValueLine = camsList.stream().anyMatch(c -> camToAdd.getName().equalsIgnoreCase(c.getName()));

        if (!updateValueLine) {
            String str = String.format("%s#%s\n", camToAdd.getName().trim(), camToAdd.getUrl().trim());
            FileUtil.saveToFile(confIni.getAbsolutePath(), str, "UTF-8", true);
            return;
        }

        String currentLine;
        try (BufferedReader reader = new BufferedReader(new FileReader(confIni));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            while ((currentLine = reader.readLine()) != null) {
                boolean writeLine = false;

                if (currentLine.isEmpty() || currentLine.trim().startsWith("#")) {
                    writeLine = true;
                }

                if (writeLine == false) {
                    Camera cam = parseLineCamera(currentLine.trim());
                    if (cam != null) {
                        if (cam.getName().equalsIgnoreCase(camToAdd.getName())) {
                            String str = String.format("%s#%s\n", camToAdd.getName().trim(), camToAdd.getUrl().trim());
                            writer.write(str);
                        }
                        else {
                            writeLine = true;
                        }
                    }
                }

                if (writeLine) {
                    writer.write(currentLine.trim() + System.getProperty("line.separator"));
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        try {
            FileUtils.copyFile(tempFile, confIni);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * Checks if a given host is reachable using ping
     * @param host
     *      The ip to check
     * @return boolean: true if host is reachable
     */
    public static boolean hostIsReachable(String host) {
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        }
        catch (UnknownHostException e) {
            return false;
        }

        // Host must be reachable 3 times to count as reachable
        int tries = 3;
        boolean isReachable;
        while(tries-- > 0) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("mm:ss:SSSS");
            LocalDateTime now = LocalDateTime.now();
            try {
                isReachable = address.isReachable(1000);
                if(!isReachable)
                    return false;
                // Avoid spamming
                TimeUnit.MILLISECONDS.sleep(100);
            }
            catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    public static BufferedImage resizeBufferedImage(BufferedImage img, Size size) {
        if (size != null && size.width != 0 && size.height != 0) {
            BufferedImage dimg = new BufferedImage((int) size.width, (int) size.height, img.getType());
            Graphics2D g2d = dimg.createGraphics();
            g2d.drawImage(img.getScaledInstance((int) size.width, (int) size.height, Image.SCALE_SMOOTH), 0, 0, null);
            g2d.dispose();
            return dimg;
        }
        else {
            //NeptusLog.pub().warn(I18n.text("Size in resizeBufferedImage must be != NULL and not 0"));
            return null;
        }
    }
}