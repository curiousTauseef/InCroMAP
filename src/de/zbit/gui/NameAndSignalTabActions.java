package de.zbit.gui;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.UIManager;

import de.zbit.data.EnrichmentObject;
import de.zbit.data.mRNA.mRNA;
import de.zbit.data.miRNA.miRNA;
import de.zbit.gui.IntegratorUI.Action;
import de.zbit.math.BenjaminiHochberg;
import de.zbit.math.Bonferroni;
import de.zbit.math.BonferroniHolm;
import de.zbit.util.StringUtil;

/**
 * Actions for the {@link JToolBar} in {@link IntegratorTabWithTable}
 * and {@link NameAndSignalsTab}s.
 * @author Clemens Wrzodek
 */
public class NameAndSignalTabActions implements ActionListener {
  
  IntegratorTabWithTable parent;
  
  public NameAndSignalTabActions(IntegratorTabWithTable parent) {
    super();
    this.parent = parent;
  }
  
  
  /**
   * All actions for {@link IntegratorTabWithTable} are defined
   * here.
   * @author Clemens Wrzodek
   */
  public static enum NSAction implements ActionCommand {
    /**
     * {@link Action} similar to  {@link #NEW_PATHWAY} but
     * immediately colors nodes according fold changes.
     */
    VISUALIZE_IN_PATHWAY,
    SEARCH_TABLE,
    INTEGRATE,
    ADD_GENE_SYMBOLS,
    ANNOTATE_TARGETS,
    CUTOFFS,
    FDR_CORRECTION_BH,
    FDR_CORRECTION_BFH,
    FDR_CORRECTION_BO;
    
    /*
     * (non-Javadoc)
     * 
     * @see de.zbit.gui.ActionCommand#getName()
     */
    public String getName() {
      switch (this) {
      /**
       * Icon is enough in search table.
       */
      case SEARCH_TABLE:
        return "Search";
      case ADD_GENE_SYMBOLS:
        return "Show gene symbols";
        
      default:
        return StringUtil.firstLetterUpperCase(toString().toLowerCase().replace('_', ' '));
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.zbit.gui.ActionCommand#getToolTip()
     */
    public String getToolTip() {
      switch (this) {
        case VISUALIZE_IN_PATHWAY:
          return "Show a KEGG pathway and color nodes accoring to fold changes.";
        case ADD_GENE_SYMBOLS:
          return "Show Gene symbols as names, using a GeneID to gene symbol converter.";
          
        default:
          return null; // Deactivate
      }
    }
  }
  
  
  
  
  public void createJToolBarItems(JToolBar bar) {
    // Not here just name, because button actions are linked to this instance!
    Class<?> tableContent = parent.getDataContentType();
    String uniqueName = parent.getClass().getSimpleName() + parent.hashCode() + tableContent.hashCode();
    if (bar.getName().equals(uniqueName)) return;
    bar.removeAll();
    bar.setName(uniqueName);
    
    
 // TODO: Create a temporary batch button.
    
    // Search
    JButton search = GUITools.createJButton(this,
        NSAction.SEARCH_TABLE, UIManager.getIcon("ICON_SEARCH_16"));
    search.setPreferredSize(new Dimension(16,16));
    bar.add(search);
    
    EnrichmentActionListener al = new EnrichmentActionListener(parent, true);
    JPopupMenu enrichment = IntegratorGUITools.createEnrichmentPopup(al);
    JDropDownButton enrichmentButton = new JDropDownButton("Enrichment", 
        UIManager.getIcon("ICON_GEAR_16"), enrichment);
    bar.add(enrichmentButton);
    
    // Visualize in Pathway
    KEGGPathwayActionListener al2 = new KEGGPathwayActionListener(parent);
    JButton showPathway = GUITools.createJButton(al2,
        NSAction.VISUALIZE_IN_PATHWAY, UIManager.getIcon("ICON_GEAR_16"));
    bar.add(showPathway);
    
    // Integrate (pair) data button
    if (!tableContent.equals(EnrichmentObject.class)) {
      JButton integrate = GUITools.createJButton(this,
          NSAction.INTEGRATE, UIManager.getIcon("ICON_GEAR_16"));
      bar.add(integrate);
    }
    
    // Datatype specific buttons:
    if (tableContent.equals(mRNA.class)) {
      bar.add(GUITools.createJButton(this,
          NSAction.ADD_GENE_SYMBOLS, UIManager.getIcon("ICON_GEAR_16")));
      
    } else if (tableContent.equals(miRNA.class)) {
      bar.add(GUITools.createJButton(this,
          NSAction.ANNOTATE_TARGETS, UIManager.getIcon("ICON_GEAR_16")));
    
    } else if (tableContent.equals(EnrichmentObject.class)) {
      bar.add(GUITools.createJButton(this,
          NSAction.CUTOFFS, UIManager.getIcon("ICON_GEAR_16")));
//      bar.add(GUITools.createJButton(this,
//          NSAction.FDR_CORRECTION_METHOD, UIManager.getIcon("ICON_GEAR_16")));
      
      JPopupMenu fdr = new JPopupMenu("FDR correction method");
      JMenuItem BH_cor = GUITools.createJMenuItem(this,
          NSAction.FDR_CORRECTION_BH, UIManager.getIcon("ICON_GEAR_16"),null,null,JCheckBoxMenuItem.class);
      fdr.add(BH_cor);
      fdr.add(GUITools.createJMenuItem(this,
          NSAction.FDR_CORRECTION_BFH, UIManager.getIcon("ICON_GEAR_16"),null,null,JCheckBoxMenuItem.class));
      fdr.add(GUITools.createJMenuItem(this,
          NSAction.FDR_CORRECTION_BO, UIManager.getIcon("ICON_GEAR_16"),null,null,JCheckBoxMenuItem.class));
      JDropDownButton fdrButton = new JDropDownButton("FDR correction method", 
          UIManager.getIcon("ICON_GEAR_16"), fdr);
      fdrButton.setToolTipText("Change the false-discovery-rate correction method.");
      
      bar.add(fdrButton);
      BH_cor.setSelected(true);
    }
    
    /* TODO:
     * if (mRNA)
     * - Show gene symbols as names
     * - Pair with miRNA
     * 
     * if (miRNA)
     * - Annotate targets
     * - Pair with mRNA
     * 
     * if (EnrichmentObject)
     * - Cutoff für > pValue, qValue, list ratio
     * - Change statistical correction
     * 
     * Always
     * - Perform Enrichment => List...
     * - Search table
     * - Visualize in pathway
     * - for (!EnrichmentObject) "integrate data" (pair data)
     * 
     * Eventuell
     * [- Add pathways] column with pathways for gene/ target
     * 
     */
    
    GUITools.setOpaqueForAllElements(bar, false);    
  }




  @SuppressWarnings("unchecked")
  @Override
  public void actionPerformed(ActionEvent e) {
    String command = e.getActionCommand();
    
    if (command.equals(NSAction.SEARCH_TABLE.toString())) {
      // Fire F3 Key for search
      if (parent.getVisualization()!=null) {
        KeyEvent F3 = new KeyEvent(parent.getVisualization(), 
            0, 0, 0, 114, (char)114);
        for (KeyListener l: parent.getVisualization().getKeyListeners()) {
          l.keyPressed(F3);
        }
      }
      
      
    } else if (command.equals(NSAction.INTEGRATE.toString())) {
      // TODO: ...
      GUITools.showMessage("Not yet implemented.", "");
      
    } else if (command.equals(NSAction.ADD_GENE_SYMBOLS.toString())) {
      try {
        mRNA.convertNamesToGeneSymbols((List<? extends mRNA>) parent.getData(), parent.getSpecies());
      } catch (Exception e1) {
        GUITools.showErrorMessage(parent, e1);
      }
      parent.getVisualization().repaint();
      
    } else if (command.equals(NSAction.ANNOTATE_TARGETS.toString())) {
      // TODO: ...
    } else if (command.equals(NSAction.CUTOFFS.toString())) {
      // TODO: ...
    } else if (command.equals(NSAction.FDR_CORRECTION_BH.toString())) {
      new BenjaminiHochberg().setQvalue((List<EnrichmentObject<Object>>) parent.getData());
      parent.getVisualization().repaint();
      
    } else if (command.equals(NSAction.FDR_CORRECTION_BFH.toString())) {
      new BonferroniHolm().setQvalue((List<EnrichmentObject<Object>>) parent.getData());
      parent.getVisualization().repaint();
      
    } else if (command.equals(NSAction.FDR_CORRECTION_BO.toString())) {
      new Bonferroni().setQvalue((List<EnrichmentObject<Object>>) parent.getData());
      parent.getVisualization().repaint();
    }
  }
  
  

}
