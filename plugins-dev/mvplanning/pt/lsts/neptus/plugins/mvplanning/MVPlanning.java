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
 * Author: tsmarques
 * 4 Nov 2015
 */
package pt.lsts.neptus.plugins.mvplanning;

import java.util.ArrayList;

import jdk.nashorn.internal.ir.debug.PrintVisitor;

import com.google.common.eventbus.Subscribe;
import com.l2fprod.common.propertysheet.DefaultProperty;

import pt.lsts.imc.EntityState;
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.QueryEntityState;
import pt.lsts.neptus.comm.IMCSendMessageUtils;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.console.ConsoleLayer;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.events.ConsoleEventMainSystemChange;
import pt.lsts.neptus.console.plugins.MainVehicleChangeListener;
import pt.lsts.neptus.console.plugins.PlanChangeListener;
import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.params.ConfigurationManager;
import pt.lsts.neptus.params.SystemProperty;
import pt.lsts.neptus.params.SystemProperty.Scope;
import pt.lsts.neptus.params.SystemProperty.ValueTypeEnum;
import pt.lsts.neptus.params.SystemProperty.Visibility;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.mvplanning.utils.VehicleAwareness;
import pt.lsts.neptus.types.map.MapType;
import pt.lsts.neptus.types.map.PlanElement;
import pt.lsts.neptus.types.mission.plan.PlanType;

/**
 * @author tsmarques
 *
 */
@PluginDescription(name = "Multi-Vehicle Planning")
public class MVPlanning extends ConsoleLayer implements PlanChangeListener {
    private String system;
    private Scope scope = Scope.GLOBAL;
    private Visibility vis = Visibility.USER;
    private ConsoleLayout console;

    public MVPlanning() {
//        this.console = getConsole();
//
//        System.out.println("######" + (console == null));
        //system = getConsole().getMainSystem();
        
        new VehicleAwareness();
    }

    private void printActiveCapabilities() {
        ArrayList<SystemProperty> prList = ConfigurationManager.getInstance().getProperties(system, vis, scope);
        for(SystemProperty pr : prList) {
            String name = pr.getName(); /* needed values */
            String categ = pr.getCategory();

            System.out.println(categ + " " + " : " + name);
        }
    }


    private void printPlanCapabilitiesNeeds(PlanType plan) {
        Maneuver[] mans = plan.getGraph().getAllManeuvers();


        for(Maneuver man : mans) {
            System.out.println("### " + man.getId());
            for(IMCMessage m : man.getStartActions().getAllMessages()) {
                for(String s : m.getValues().keySet()) {
                    if(s.equals("name"))
                        System.out.println(m.getValue(s).toString());
                }
            }
        }
    }

    void requestEntitiesState() {
        QueryEntityState qEntityState = new QueryEntityState();
        ImcMsgManager.getManager().sendMessageToSystem(qEntityState, system);
    }

//    @Subscribe
//    public void on(EntityState msg) {
//        System.out.println("# MSG FROM: " + msg.getEntityName() + " @ " + msg.getSourceName());
//        System.out.println("# ENTITY STATE: " + msg.getStateStr());
//    }

    @Override
    public boolean userControlsOpacity() {
        return false;
    }

    @Override
    public void initLayer() {
        this.console = getConsole();
//        system = console.getMainSystem();
//        printActiveCapabilities();
    }

    @Override
    public void cleanLayer() {
    }



//    @Subscribe
//    public void on(ConsoleEventMainSystemChange ev) { /* When a diff vehicle has been selected as main Vehicle */
//        system = console.getMainSystem();
//
//        //printActiveCapabilities();
//        printPlanCapabilitiesNeeds(console.getPlan());
//    }


    @Override
    public void planChange(PlanType plan) {
        //printPlanCapabilitiesNeeds(plan);
    }
}
