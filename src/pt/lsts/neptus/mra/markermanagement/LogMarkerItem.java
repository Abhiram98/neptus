/*
 * Copyright (c) 2004-2015 Universidade do Porto - Faculdade de Engenharia
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
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Manuel R.
 * Feb 12, 2015
 */
package pt.lsts.neptus.mra.markermanagement;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DateFormat;

import pt.lsts.neptus.mra.LogMarker;
import pt.lsts.neptus.types.coord.LocationType;

/**
 * @author Manuel
 *
 */
public class LogMarkerItem extends LogMarker {

    private static final long serialVersionUID = 1L;
    private int index;
    private BufferedImage image;
    private File sidescanImgPath;
    private File drawImgPath;
    private String annotation;
    private double altitude;
    private Classification classification;
    
    public enum Classification {
        UNDEFINED(-1), 
        NONE(1), 
        CABLE(2), 
        PIPE(3), 
        ROCK(4), 
        WRECK(5),
        UNKOWN(6);

        public int getValue() {
            return this.value;
        }

        private int value;

        private Classification(int value) {
            this.value = value;
        }

    }

    /**
     * @param label
     * @param timestamp
     * @param lat
     * @param lon
     */
    public LogMarkerItem(int index, String label, double timestamp, double lat, double lon, File sidescanImgPath, String annot, double altitude, Classification classif) {
        super(label, timestamp, lat, lon);
        this.index = index;
        this.sidescanImgPath = sidescanImgPath;
        this.annotation = annot;
        this.altitude = altitude;
        this.classification = classif;
        //System.out.println(toString());
    }

    /**
     * @return the image
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * @param image the image to set
     */
    public void setImage(BufferedImage image) {
        this.image = image;
    }

    /**
     * @return the classification
     */
    public Classification getClassification() {
        return classification;
    }

    /**
     * @param classification the classification to set
     */
    public void setClassification(Classification classification) {
        this.classification = classification;
    }

    /**
     * @return the annotation
     */
    public String getAnnotation() {
        return annotation;
    }

    /**
     * @param annotation the annotation to set
     */
    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    /**
     * @return the altitude
     */
    public double getAltitude() {
        return altitude;
    }

    /**
     * @param depth the depth to set
     */
    public void setAltitude(double depth) {
        this.altitude = depth;
    }

    /**
     * @return the index
     */
    public int getIndex() {
        return index;
    }

    /**
     * @param index the index to set
     */
    public void setIndex(int index) {
        this.index = index;
    }
    
    public LocationType getLocation() {
        return new LocationType(Math.toDegrees(getLat()), Math.toDegrees(getLon()));
    }

    public String toString(){
        StringBuilder string = new StringBuilder();
        string.append(index + " ");
        string.append(getLabel() + " ");
        string.append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(getDate()) + " ");
        string.append(getLat() + " ");
        string.append(getLon() + " ");
        string.append(getAltitude()+ " ");
        string.append(getAnnotation()+ " ");
        string.append(getClassification()+ " ");

        return string.toString();
    }

    public void copy(LogMarkerItem from) {
        this.annotation = from.annotation;
        this.classification = from.classification;
        this.image = from.image;
    }

    /**
     * @return the sidescanImgPath
     */
    public File getSidescanImgPath() {
        return sidescanImgPath;
    }

    /**
     * @param sidescanImgPath the sidescanImgPath to set
     */
    public void setSidescanImgPath(File sidescanImgPath) {
        this.sidescanImgPath = sidescanImgPath;
    }


}
