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
 * Author: zp
 * Nov 25, 2015
 */
package dk.maridan;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import javax.swing.ProgressMonitor;
import javax.xml.bind.JAXB;

import dk.maridan.SurveyPlan.Manoeuvre;
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.SetEntityParameters;
import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.mp.maneuvers.Goto;
import pt.lsts.neptus.mp.maneuvers.RowsManeuver;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.map.PlanUtil;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.types.mission.plan.IPlanFileExporter;
import pt.lsts.neptus.types.mission.plan.PlanType;

/**
 * @author zp
 *
 */
@PluginDescription
public class MaridanPlanExporter implements IPlanFileExporter {

    @Override
    public String getExporterName() {
        return "Maridan Plan";
    }

    @Override
    public void exportToFile(PlanType plan, File out, ProgressMonitor monitor) throws Exception {
        String xml = translate(plan, null);
        Files.write(out.toPath(), xml.getBytes(), StandardOpenOption.CREATE);
    }

    @Override
    public String[] validExtensions() {
        return new String[] { "xml" };
    }

    public static String translate(PlanType plan, LocationType vehicleLocation) throws Exception {

        SurveyPlan out = new SurveyPlan();
        LocationType previousPos = vehicleLocation;
        for (Maneuver m : plan.getGraph().getManeuversSequence()) {

            Manoeuvre man = null;
            switch (m.getType()) {
                case "Goto": {
                    Goto g = (Goto) m;
                    SurveyPlan.GotoMan tmp = new SurveyPlan.GotoMan();
                    man = tmp;
                    switch (g.getManeuverLocation().getZUnits()) {
                        case DEPTH:
                            tmp.setDepth((float) g.getManeuverLocation().getZ());
                            break;
                        case ALTITUDE:
                        case HEIGHT:
                            tmp.setAltitude((float) g.getManeuverLocation().getZ());
                            break;
                        default:
                            throw new Exception("Invalid Z units for maneuver " + m.getId());
                    }
                    tmp.setLatDegs(g.getManeuverLocation().getLatitudeDegs());
                    tmp.setLonDegs(g.getManeuverLocation().getLongitudeDegs());
                    tmp.setSpeedMps((float) g.getSpeed());

                    if (previousPos == null)
                        tmp.setTimeoutSecs(1000f);
                    else
                        tmp.setTimeoutSecs((float) PlanUtil.getExecutionTimeSecs(previousPos, g) * 1.5f);

                    previousPos = g.getEndLocation();
                    break;
                }
                case "RowsManeuver": {
                    RowsManeuver rows = (RowsManeuver) m;
                    SurveyPlan.SiteMan site = new SurveyPlan.SiteMan();
                    man = site;
                    switch (rows.getManeuverLocation().getZUnits()) {
                        case DEPTH:
                            site.setDepth((float) rows.getManeuverLocation().getZ());
                            break;
                        case ALTITUDE:
                        case HEIGHT:
                            site.setAltitude((float) rows.getManeuverLocation().getZ());
                            break;
                        default:
                            throw new Exception("Invalid Z units for maneuver " + m.getId());
                    }
                    site.setLatDegs(rows.getManeuverLocation().getLatitudeDegs());
                    site.setLonDegs(rows.getManeuverLocation().getLongitudeDegs());
                    site.setSpeedMps((float) rows.getSpeed());
                    site.setSpacingMeters((float) rows.getHstep());
                    if (!rows.isFirstCurveRight())
                        site.setSpacingMeters((float) -rows.getHstep());
                    site.setLegCount((int) Math.floor(rows.getWidth() / rows.getHstep()) + 1);
                    site.setDirectionDegs((float) Math.toDegrees(rows.getBearingRad()));
                    site.setLength((float)rows.getLength());
                    if (previousPos == null)
                        site.setTimeoutSecs(1000f);
                    else
                        site.setTimeoutSecs((float) PlanUtil.getExecutionTimeSecs(previousPos, rows) * 1.5f);

                    previousPos = rows.getEndLocation();
                    break;
                }
                default:
                    break;
            }
            if (man != null) {

                for (IMCMessage imc : m.getStartActions().getAllMessages()) {
                    if (imc instanceof SetEntityParameters) {
                        String entityName = ((SetEntityParameters) imc).getName();
                        if (entityName.equals("Payload")) {
                            String profile = ((SetEntityParameters) imc).getParams().firstElement().getValue();
                            switch (profile) {
                                case "SSS+SBP":
                                    man.payload.set("1");
                                    break;
                                case "Camera":
                                    man.payload.set("2");
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
                out.addManoeuvre(man);
            }
        }
        StringWriter writer = new StringWriter();
        JAXB.marshal(out, writer);
        return writer.toString();
    }

    public static void main(String[] args) throws Exception {
        MissionType mt = new MissionType("/home/zp/workspace/neptus/missions/APDL/missao-apdl.nmisz");
        PlanType plan = mt.getIndividualPlansList().get("plan1");
        String planXml = MaridanPlanExporter.translate(plan, null);
        System.out.println(planXml);
    }
}
