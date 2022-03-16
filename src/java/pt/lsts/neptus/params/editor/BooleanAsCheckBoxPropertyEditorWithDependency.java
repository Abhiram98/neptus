/*
 * Copyright (c) 2004-2022 Universidade do Porto - Faculdade de Engenharia
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
 * Author: Paulo Dias
 * 15/03/2022
 */
package pt.lsts.neptus.params.editor;

import com.l2fprod.common.beans.editor.BooleanAsCheckBoxPropertyEditor;
import javax.swing.JCheckBox;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.params.SystemProperty;
import pt.lsts.neptus.params.editor.PropertyEditorChangeValuesIfDependencyAdapter.ValuesIf;

public class BooleanAsCheckBoxPropertyEditorWithDependency<T extends Boolean> extends BooleanAsCheckBoxPropertyEditor
        implements PropertyChangeListener {

    private LinkedHashMap<String, Object> dependencyVariables = new LinkedHashMap<>();
    private PropertyEditorChangeValuesIfDependencyAdapter<?, T> pec = null;

    private ActionListener[] actionListenersList;
    private ActionListener actionListener;

    public BooleanAsCheckBoxPropertyEditorWithDependency() {
        this(null, null);
    }

    public BooleanAsCheckBoxPropertyEditorWithDependency(T startValue,
            PropertyEditorChangeValuesIfDependencyAdapter<?, ?> pec) {
        super();
//        actionListenersList = ((JCheckBox) this.editor).getActionListeners();
//        for (ActionListener al : actionListenersList) {
//            ((JCheckBox) this.editor).removeActionListener(al);
//        }

        if (startValue != null) {
            ((JCheckBox) this.editor).setSelected(startValue.booleanValue());
        }

//        actionListener = new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                for (ActionListener al : actionListenersList) {
//                    al.actionPerformed(e);
//                }
//            }
//        };
//        ((JCheckBox) this.editor).addActionListener(actionListener);

        ((JCheckBox) this.editor).addChangeListener((changeEvent) -> {
            JCheckBox src = (JCheckBox) changeEvent.getSource();
            boolean newVal = src.isSelected();
            validate();
        });

        this.pec = (PropertyEditorChangeValuesIfDependencyAdapter<?, T>) pec;
    }

    public BooleanAsCheckBoxPropertyEditorWithDependency(PropertyEditorChangeValuesIfDependencyAdapter<?, T> pec) {
        this(null, pec);
    }

    @Override
    public Object getValue() {
        return super.getValue();
    }

    @Override
    public void setValue(Object value) {
        super.setValue(value);
    }

    private void updateDependenciesVariables() {
        if (pec == null || pec.valuesIfTests.isEmpty()) {
            dependencyVariables.clear();
        }
        else {
            for (PropertyEditorChangeValuesIfDependencyAdapter.ValuesIf<?, T> vif : pec.valuesIfTests) {
                if (!dependencyVariables.containsKey(vif.dependantParamId))
                    dependencyVariables.put(vif.dependantParamId, null);
            }
        }
    }

    /* (non-Javadoc)
     * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (pec == null || pec.valuesIfTests.isEmpty()) {
            return;
        }

        updateDependenciesVariables();

        boolean testVariablePresent = false;
        boolean passedAtLeastOneTest = false;

        ValuesIf<?, T> toChangeTest = null;
        if(evt.getSource() instanceof SystemProperty) {
            SystemProperty sp = (SystemProperty) evt.getSource();

            if (dependencyVariables.containsKey(sp.getName())) {
                dependencyVariables.put(sp.getName(), sp.getValue());
            } else {
                return;
            }

//            for (String testVarKey : dependencyVariables.keySet()) {
//                Object testVarValue = dependencyVariables.get(testVarKey);
//                if (testVarValue == null)
//                    continue;
//
//
//                for (int i = 0; i < pec.getValuesIfTests().size(); i++) {
//                    ValuesIf<?, T> vl = (ValuesIf<?, T>) pec.getValuesIfTests().get(i);
//
//                    if (!vl.dependantParamId.equals(testVarKey))
//                        continue;
//
//                    testVariablePresent = true;
//                    boolean isPassedTest = false;
//
//                    try {
//                        switch (vl.op) {
//                            case EQUALS:
//                                if (vl.testValue instanceof Number)
//                                    isPassedTest = ((Number) vl.testValue).doubleValue() == ((Number) testVarValue).doubleValue();
//                                else if (vl.testValue instanceof Boolean)
//                                    isPassedTest = (Boolean) vl.testValue == (Boolean) testVarValue;
//                                else if (vl.testValue instanceof String)
//                                    isPassedTest = ((String) vl.testValue).equals((String) testVarValue);
//                                else
//                                    isPassedTest = vl.testValue.equals(testVarValue);
//                                break;
//                        }
//                    }
//                    catch (Exception e) {
//                        NeptusLog.pub()
//                                .warn("Problem while evaluating test variable " + testVarKey + ": " + e.getMessage());
//                    }
//
//                    if (isPassedTest) {
//                        if (vl.values.isEmpty())
//                            continue;
//
//                        passedAtLeastOneTest = true;
//                        toChangeTest = vl;
//
//                        break;
//                    }
//                }
//
//                if(passedAtLeastOneTest)
//                    break;
//            }
        }

//        if (testVariablePresent && passedAtLeastOneTest) {
//            ArrayList<T> valuesAdmissible = toChangeTest.values;
//            JCheckBox editorCheckBox = ((JCheckBox) this.editor);
//            boolean needsChange = true;
//            for (T v : valuesAdmissible) {
//                if (editorCheckBox.isSelected() == v.booleanValue()) {
//                    needsChange = false;
//                }
//            }
//            if (needsChange) {
//                //editorCheckBox.setSelected(toChangeTest.values.get(0).booleanValue());
//                //firePropertyChange(!editorCheckBox.isSelected(), editorCheckBox.isSelected());
//                //editorCheckBox.doClick(50);
//                //editorCheckBox.revalidate();
//                //editorCheckBox.repaint();
//                setValue(toChangeTest.values.get(0).booleanValue());
//            }
//        }
//        // if (testVariablePresent && !passedAtLeastOneTest) {}

        validate();
    }

    private void validate() {
        updateDependenciesVariables();

        boolean testVariablePresent = false;
        boolean passedAtLeastOneTest = false;

        ValuesIf<?, T> toChangeTest = null;

        for (String testVarKey : dependencyVariables.keySet()) {
            Object testVarValue = dependencyVariables.get(testVarKey);
            if (testVarValue == null)
                continue;


            for (int i = 0; i < pec.getValuesIfTests().size(); i++) {
                ValuesIf<?, T> vl = (ValuesIf<?, T>) pec.getValuesIfTests().get(i);

                if (!vl.dependantParamId.equals(testVarKey))
                    continue;

                testVariablePresent = true;
                boolean isPassedTest = false;

                try {
                    switch (vl.op) {
                        case EQUALS:
                            if (vl.testValue instanceof Number)
                                isPassedTest = ((Number) vl.testValue).doubleValue() == ((Number) testVarValue).doubleValue();
                            else if (vl.testValue instanceof Boolean)
                                isPassedTest = (Boolean) vl.testValue == (Boolean) testVarValue;
                            else if (vl.testValue instanceof String)
                                isPassedTest = ((String) vl.testValue).equals((String) testVarValue);
                            else
                                isPassedTest = vl.testValue.equals(testVarValue);
                            break;
                    }
                }
                catch (Exception e) {
                    NeptusLog.pub()
                            .warn("Problem while evaluating test variable " + testVarKey + ": " + e.getMessage());
                }

                if (isPassedTest) {
                    if (vl.values.isEmpty())
                        continue;

                    passedAtLeastOneTest = true;
                    toChangeTest = vl;

                    break;
                }
            }

            if(passedAtLeastOneTest)
                break;
        }

        if (testVariablePresent && passedAtLeastOneTest) {
            ArrayList<T> valuesAdmissible = toChangeTest.values;
            JCheckBox editorCheckBox = ((JCheckBox) this.editor);
            boolean needsChange = true;
            for (T v : valuesAdmissible) {
                if (editorCheckBox.isSelected() == v.booleanValue()) {
                    needsChange = false;
                }
            }
            if (needsChange) {
                //editorCheckBox.setSelected(toChangeTest.values.get(0).booleanValue());
                //firePropertyChange(!editorCheckBox.isSelected(), editorCheckBox.isSelected());
                //editorCheckBox.doClick(50);
                //editorCheckBox.revalidate();
                //editorCheckBox.repaint();
                setValue(toChangeTest.values.get(0).booleanValue());
            }
        }
        // if (testVariablePresent && !passedAtLeastOneTest) {}
    }
}
