/*
 * $Id$
 * $URL$
 * ---------------------------------------------------------------------
 * This file is part of Integrator, a program integratively analyze
 * heterogeneous microarray datasets. This includes enrichment-analysis,
 * pathway-based visualization as well as creating special tabular
 * views and many other features. Please visit the project homepage at
 * <http://www.cogsys.cs.uni-tuebingen.de/software/InCroMAP> to
 * obtain the latest version of Integrator.
 *
 * Copyright (C) 2011-2015 by the University of Tuebingen, Germany.
 *
 * Integrator is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation. A copy of the license
 * agreement is provided in the file named "LICENSE.txt" included with
 * this software distribution and also available online as
 * <http://www.gnu.org/licenses/lgpl-3.0-standalone.html>.
 * ---------------------------------------------------------------------
 */
package de.zbit.gui.dialogs;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.treetable.JTreeTable;

import de.zbit.data.HeterogeneousData;
import de.zbit.data.NameAndSignals;
import de.zbit.data.PairedNS;
import de.zbit.data.Signal.MergeType;
import de.zbit.data.Signal.SignalType;
import de.zbit.data.TableResult;
import de.zbit.data.compound.Compound;
import de.zbit.data.mRNA.mRNA;
import de.zbit.data.methylation.DNAmethylation;
import de.zbit.data.miRNA.miRNA;
import de.zbit.data.protein.ProteinModificationExpression;
import de.zbit.graph.gui.TranslatorPanel;
import de.zbit.gui.GUITools;
import de.zbit.gui.IntegratorUI;
import de.zbit.gui.IntegratorUITools;
import de.zbit.gui.JLabeledComponent;
import de.zbit.gui.JOptionPane2;
import de.zbit.gui.actions.TranslatorTabActions.TPAction;
import de.zbit.gui.actions.listeners.KEGGPathwayActionListener;
import de.zbit.gui.customcomponents.TableResultTableModel;
import de.zbit.gui.layout.LayoutHelper;
import de.zbit.gui.prefs.SignalOptionPanel;
import de.zbit.gui.tabs.NameAndSignalsTab;
import de.zbit.kegg.Translator;
import de.zbit.kegg.gui.OrganismSelector;
import de.zbit.kegg.gui.PathwaySelector;
import de.zbit.util.ArrayUtils;
import de.zbit.util.Species;
import de.zbit.util.objectwrapper.LabeledObject;
import de.zbit.util.objectwrapper.ValuePair;
import de.zbit.util.objectwrapper.ValueTriplet;

/**
 * Shows a dialog that lets the user chooser a Pathway
 * and one dataset per data type.
 * @author Clemens Wrzodek
 * @version $Rev$
 */
public class IntegrationDialog extends JPanel implements ActionListener {
  private static final long serialVersionUID = 7451686453282214876L;

  /**
   * Data types, that are at the same time in one pathway visualizable.
   * The user may choose one dataset each.
   */
  @SuppressWarnings("unchecked")
  private Class<? extends NameAndSignals>[] toVisualize = new Class[]{
    mRNA.class, miRNA.class, DNAmethylation.class, ProteinModificationExpression.class,Compound.class
  };
  
  /**
   * Organism selector
   */
  private OrganismSelector orgSel=null;
  
  /**
   * Pathway selector
   */
  private PathwaySelector pwSel=null;

  /**
   * Should the data type be visualized?
   */
  private JCheckBox[] visDataType;

  /**
   * Selected datasets to visualize
   */
  private JLabeledComponent[] dataSelect;

  /**
   * Experiments from the selected datasets ({@link #dataSelect}) to visualize
   */
  private JLabeledComponent[] expSelect;
  
  /**
   * Allows to select a individual {@link MergeType} for each data type.
   */
  private JLabeledComponent[] mergeSelect;
  
  /**
   * A dirty static way to set an default selection on the dialog.
   */
  public static NameAndSignalsTab defaultSelection = null;
  
  
  public IntegrationDialog(boolean showPathwaySelector, boolean showMergeTypeSelectos) throws Exception {
    createIntegrationDialog(showPathwaySelector, showMergeTypeSelectos);
  }
  
  /* Constructor needed to hide potential entities from integration dialog
   * e.g., Compounds from "Integrate heterogeneous data" */
  public IntegrationDialog(boolean showPathwaySelector, boolean showMergeTypeSelectos, Class<? extends NameAndSignals>[] toVisualize) throws Exception {
    this.toVisualize = toVisualize;
  	createIntegrationDialog(showPathwaySelector, showMergeTypeSelectos);
  }
  
  private void createIntegrationDialog(final boolean showPathwaySelector, boolean showMergeTypeSelectos) throws Exception {
    //KEGGpwAL.visualizeAndColorPathway(); //<= examples
    LayoutHelper lh = new LayoutHelper(this);
    
    // Create organism and pathway selector
    if (showPathwaySelector) {
      pwSel = new PathwaySelector(Translator.getFunctionManager(),null, IntegratorUITools.organisms);
      orgSel = pwSel.getOrganismSelector();
    } else {
      orgSel = new OrganismSelector(Translator.getFunctionManager(), null, IntegratorUITools.organisms);
    }
    orgSel.addOrganismBoxLoadedCompletelyListener(this);
    lh.add(showPathwaySelector?pwSel:orgSel);
    MergeType defaultMergeSelection=(showMergeTypeSelectos)?IntegratorUITools.getMergeTypeSilent():null;

    
    // Create selectors for each data type
    visDataType = new JCheckBox[toVisualize.length];
    dataSelect = new JLabeledComponent[toVisualize.length];
    expSelect = new JLabeledComponent[toVisualize.length];
    mergeSelect = new JLabeledComponent[toVisualize.length];
    int i=-1;
    for (Class<? extends NameAndSignals> type : toVisualize) {
      i++;
      JPanel curDS = new JPanel();
      LayoutHelper ld = new LayoutHelper(curDS);
      String name = String.format("%s %s data", showPathwaySelector?"Visualize":"Integrate", PairedNS.getTypeNameFull(type));
      final JCheckBox visData = new JCheckBox(name);
      visDataType[i] = visData;
      ld.add(visDataType[i]);
      
      final JLabeledComponent dataSetSelector = new JLabeledComponent("Select a dataset",true,new String[]{"N/A"});
      dataSelect[i] = dataSetSelector;
      ld.add(dataSelect[i]);
      
      final JLabeledComponent experimentSelector = IntegratorUITools.createSelectExperimentBox(null);
      expSelect[i] = experimentSelector;
      ld.add(expSelect[i]);
      
      final JLabeledComponent mergeTypeSelector;
      if (showMergeTypeSelectos) {
        mergeTypeSelector = new JLabeledComponent("Gene-center observations by",true,MergeType.values());
        //SignalOptionPanel.removeItemFromJComboBox(mergeTypeSelector, MergeType.AskUser);
        SignalOptionPanel.performCommonSignalOptionPanelModifications(mergeTypeSelector);
        mergeTypeSelector.setDefaultValue(defaultMergeSelection);
        mergeSelect[i] = mergeTypeSelector;
        ld.add(mergeSelect[i]);  
      } else {
        mergeTypeSelector=null;
      }
      
      // Change experiment box upon dataset selection
      dataSelect[i].addActionListener(new ActionListener() {
        @SuppressWarnings("unchecked")
        @Override
        public void actionPerformed(ActionEvent e) {
          NameAndSignalsTab tab = ((LabeledObject<NameAndSignalsTab>)dataSetSelector.getSelectedItem()).getObject();
          IntegratorUITools.createSelectExperimentBox(experimentSelector, (NameAndSignals)tab.getExampleData());
          // Only show p-values for DNA methylation data
          if (showPathwaySelector) { // i.e. integrated pw-based visualization
            IntegratorUITools.modifyExperimentBoxForDNAMethylation(experimentSelector, (NameAndSignals)tab.getExampleData());
          }
        }
      });
      // Enable/ disable others upon CheckBox selection
      visDataType[i].addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          dataSetSelector.setEnabled(visData.isSelected());
          experimentSelector.setEnabled(visData.isSelected());
          if (mergeTypeSelector!=null) mergeTypeSelector.setEnabled(visData.isSelected());
        }
      });
      
      curDS.setBorder(new TitledBorder(PairedNS.getTypeNameFull(type)));
      lh.add(curDS);
    }
    
    
    // Write current data sets to selectors and update on species selection
    updateExperimentSelectionBoxes();
    
    // Listener on species box and others
    if (orgSel!=null) {
      orgSel.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateExperimentSelectionBoxes();
        }
      });
    }

    /* Created the following dialog for each type :
     * 
     * [ ] Visualize (mRNA-PairNS.getTypeName()) data
     *     (________) datasets (same species as in box1 !?!?)
     *     (________) experiments
     *     
     * Disable by default if none is available [for any species].
     * 
     */
  }

  /**
   * @return {@link Species} from current selector or null if none selected
   */
  private Species getSpeciesFromSelector() {
    String abbr = orgSel==null?null:orgSel.getSelectedOrganismAbbreviation();
    return Species.search(IntegratorUITools.organisms, abbr, Species.KEGG_ABBR);
  }
  
  /**
   * @return
   */
  public PathwaySelector getPathwaySelector() {
    return pwSel;
  }
  
  /**
   * 
   */
  @SuppressWarnings("unchecked")
  private void updateExperimentSelectionBoxes() {
    boolean showPathwaySelector = (pwSel!=null && pwSel.isVisible());
    Species species = getSpeciesFromSelector();
    
    // Create a list of available datasets  
    int i=-1;
    for (Class<? extends NameAndSignals> type : toVisualize) {
      i++;
      List<LabeledObject<NameAndSignalsTab>> datasets = IntegratorUITools.getNameAndSignalTabsWithSignals(species, type);
      if (showPathwaySelector && DNAmethylation.class.isAssignableFrom(type)) { // i.e. integrated pw-based visualization of DNAm data
        // Remove all DNA methylation datasets that contain no p-value signals
        //IntegratorUITools.filterNSTabs(datasets, DNAmethylation.class, SignalType.pValue); //Keyword: DNAm-pValue
      }
      if (datasets==null || datasets.size()<1) {
        // Disable all and deselect checkbox
        visDataType[i].setSelected(false);
        visDataType[i].setEnabled(false);
      } else {
        visDataType[i].setEnabled(true);
        dataSelect[i].setHeaders(datasets);
        NameAndSignalsTab nsTab = ((LabeledObject<NameAndSignalsTab>)dataSelect[i].getSelectedItem()).getObject();
        IntegratorUITools.createSelectExperimentBox(expSelect[i], (NameAndSignals)nsTab.getExampleData());
        // Only show p-values for DNA methylation data
        if (showPathwaySelector) { // i.e. integrated pw-based visualization
          IntegratorUITools.modifyExperimentBoxForDNAMethylation(expSelect[i], (NameAndSignals)nsTab.getExampleData());
        }
        // TODO: does this also change the experiment box?
        //dataSelect[i].fireActionListeners();
      }
      
      dataSelect[i].setEnabled(visDataType[i].isSelected());
      expSelect[i].setEnabled(visDataType[i].isSelected());
      if (mergeSelect[i]!=null) mergeSelect[i].setEnabled(visDataType[i].isSelected());
    }
  }
  
  
  public static IntegrationDialog showIntegratedVisualizationDialog() {
    return showIntegratedVisualizationDialog(IntegratorUI.getInstance());
  }
  public static IntegrationDialog showIntegratedVisualizationDialog(Component parent) {
    IntegrationDialog integratedVis;
    try {
      integratedVis = new IntegrationDialog(true, false);
    } catch (Exception e) {
      GUITools.showErrorMessage(null, e);
      return null;
    }
    
    // Show and evaluate dialog
    int ret = JOptionPane2.showConfirmDialogResizable(parent, integratedVis, "Integrated data visualization", JOptionPane.OK_CANCEL_OPTION);
    if (ret!=JOptionPane.OK_OPTION) return null;
    else return integratedVis;
  }
  
  @SuppressWarnings("unchecked")
  public static IntegrationDialog showIntegratedTreeTableDialog(Component parent) {
    IntegrationDialog integratedTbl;
    try {
      integratedTbl = new IntegrationDialog(false, true,
      		new Class[]{mRNA.class, miRNA.class, DNAmethylation.class, ProteinModificationExpression.class});
    } catch (Exception e) {
      GUITools.showErrorMessage(null, e);
      return null;
    }
    
    // Show and evaluate dialog
    int ret = JOptionPane2.showConfirmDialogResizable(parent, integratedTbl, "Data integration", JOptionPane.OK_CANCEL_OPTION);
    if (ret!=JOptionPane.OK_OPTION) return null;
    else return integratedTbl;
  }
  
  /**
   * Shows the {@link IntegrationDialog} and
   * evaluates the selection by adding a pathway tab
   * to the current {@link IntegratorUI#instance}.
   * Also sets all data to visualize.
   */
  @SuppressWarnings("unchecked")
  public static void showAndEvaluateIntegratedVisualizationDialog() {
    IntegratorUI ui = IntegratorUI.getInstance();
    IntegrationDialog dialog = showIntegratedVisualizationDialog(ui);
    if (dialog!=null) {
      // Evaluate and eventually open new tab.
      if (dialog.getPathwaySelector().getSelectedPathwayID()!=null) {
        List<ValueTriplet<NameAndSignalsTab, String, SignalType>> dataSource = new LinkedList<ValueTriplet<NameAndSignalsTab,String,SignalType>>();
        for (int i=0; i<dialog.visDataType.length; i++) {
          if (dialog.visDataType[i].isEnabled() && dialog.visDataType[i].isSelected()) {
            ValuePair<String, SignalType> expSignal = (ValuePair<String, SignalType>) dialog.expSelect[i].getSelectedItem();
            NameAndSignalsTab nsTab = ((LabeledObject<NameAndSignalsTab>)dialog.dataSelect[i].getSelectedItem()).getObject();
            dataSource.add(new ValueTriplet<NameAndSignalsTab, String, SignalType>(
                nsTab, expSignal.getA(), expSignal.getB()));
          }          
        }


        // Open pathway and set experiments to visualize.
        KEGGPathwayActionListener al = new KEGGPathwayActionListener(null);
        TranslatorPanel<?> tp = al.visualizePathway(dialog.getPathwaySelector());
        tp.setData(TPAction.VISUALIZE_DATA.toString(), dataSource);
      }
    }
  }

  public static <T> List<T> getAsList(Collection<T> col) {
    if (col==null) return (List<T>) null;
    else if (col instanceof List) {
      return (List<T>) col;
    } else {
      return new ArrayList<T>(col);
    }
  }
  
  /**
   * Shows the {@link IntegrationDialog} and
   * evaluates the selection by adding a TreeTable tab
   * to the current {@link IntegratorUI#instance}.
   */
  @SuppressWarnings("unchecked")
  public static void showAndEvaluateIntegratedTreeTableDialog() {
    IntegratorUI ui = IntegratorUI.getInstance();
    IntegrationDialog dialog = showIntegratedTreeTableDialog(ui);
    if (dialog!=null) {
      // Evaluate and eventually open new tab.
      final HeterogeneousData visualizer = new HeterogeneousData();
      Species species = null;
      for (int i=0; i<dialog.visDataType.length; i++) {
        if (dialog.visDataType[i].isEnabled() && dialog.visDataType[i].isSelected()) {
          ValuePair<String, SignalType> expSignal = (ValuePair<String, SignalType>) dialog.expSelect[i].getSelectedItem();
          NameAndSignalsTab nsTab = ((LabeledObject<NameAndSignalsTab>)dialog.dataSelect[i].getSelectedItem()).getObject();
          MergeType mergeType = MergeType.valueOf(dialog.mergeSelect[i].getSelectedItem().toString());
          
          // Check if we have to annotate miRNA with targets
          if (miRNA.class.isAssignableFrom(NameAndSignals.getType(nsTab.getExampleData()))) {
            if (!miRNA.hasTargets((Iterable<? extends miRNA>) nsTab.getData())) {
              nsTab.getActions().annotateMiRNAtargets();
            }
          }
          //---
          
          visualizer.addDataType(getAsList(nsTab.getData()), expSignal, mergeType);
          species = nsTab.getSpecies();
        }          
      }
      if (species==null || dialog.visDataType.length<1) {
        GUITools.showErrorMessage(ui, "Please make a valid selection.");
        return;
      }
      
      // Is usually fast, so no need for another thread here.
      visualizer.initTree(species);

      // Create NameAndSignalsTab with customized table
      NameAndSignalsTab nsTab = new NameAndSignalsTab(IntegratorUI.getInstance(), (null), species) {
        private static final long serialVersionUID = 7415047130386194731L;
        @Override
        protected void createTable() {
          synchronized (super.table==null ? this : super.table) {
            super.table = new JTreeTable(visualizer, true);
          }
          data = (List<? extends TableResult>) ((JTreeTable)table).getFirstRowAsList();
          
          // Set Renderers and add search capabilities
          TableResultTableModel.buildJTable(super.table.getModel(), getSpecies(), super.table);
          super.table.setRowSorter(null); // TreeTables are not sortable.
          super.table.getTableHeader().setReorderingAllowed(false); // Else: Collapsing does not work.
        };
        
        @Override
        public Object getExampleData() {
          // Gene nodes have all available signals.
          return visualizer.getFirstGeneNode();
        }        
      };
      
      // Add tab to current UI
      IntegratorUI.getInstance().addTab(nsTab, "IntegratedData", null);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getActionCommand().toString().equals(OrganismSelector.LOADING_COMPLETE_ACTION_COMMAND)) {
      setAndEraseDefaultSelection();
    }
  }

  /**
   * Set all values to {@link #defaultSelection} and set
   * this variable to <code>NULL</code> afterwards.
   * <p>May only be called if all loading is complete
   * (also the Organism box!)
   */
  private void setAndEraseDefaultSelection() {
    // Should we set an initial default selection?
    if (defaultSelection!=null && defaultSelection.getSpecies(false)!=null) {
      orgSel.setDefaultSelection(defaultSelection.getSpecies().getScientificName());
      Class<? extends NameAndSignals> type = NameAndSignals.getType(defaultSelection.getExampleData());
      int id = ArrayUtils.indexOf(toVisualize, type);
      if (id>=0) {
        visDataType[id].setSelected(true);
        updateExperimentSelectionBoxes();
        int setId = LabeledObject.getIndexOfObject(dataSelect[id].getHeaders(), defaultSelection);
        if (setId>=0) dataSelect[id].setSelectedValue(setId);
      }
    }
    defaultSelection=null;
  }
  
}
