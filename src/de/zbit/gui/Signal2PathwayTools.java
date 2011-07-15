/**
 * @author Clemens Wrzodek
 */
package de.zbit.gui;

import java.awt.Color;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingWorker;

import y.base.DataMap;
import y.base.Node;
import y.base.NodeMap;
import y.view.Graph2D;
import y.view.NodeRealizer;
import de.zbit.data.EnrichmentObject;
import de.zbit.data.NameAndSignals;
import de.zbit.data.Signal;
import de.zbit.data.Signal.MergeType;
import de.zbit.data.Signal.SignalType;
import de.zbit.gui.prefs.PathwayVisualizationOptions;
import de.zbit.kegg.Translator;
import de.zbit.kegg.TranslatorTools;
import de.zbit.kegg.ext.GenericDataMap;
import de.zbit.kegg.gui.KGMLSelectAndDownload;
import de.zbit.kegg.gui.TranslatorPanel;
import de.zbit.kegg.io.BatchKEGGtranslator;
import de.zbit.kegg.io.KEGG2yGraph;
import de.zbit.kegg.io.KEGGtranslatorIOOptions.Format;
import de.zbit.math.LinearRescale;
import de.zbit.parser.Species;
import de.zbit.util.AbstractProgressBar;
import de.zbit.util.StringUtil;
import de.zbit.util.Utils;
import de.zbit.util.ValuePair;
import de.zbit.util.prefs.SBPreferences;

/**
 * This class is intended to provide tools for connecting {@link Signal}s
 * to KEGG Pathways in {@link Graph2D}.
 * @author Clemens Wrzodek
 */
public class Signal2PathwayTools {
  public static final transient Logger log = Logger.getLogger(Signal2PathwayTools.class.getName());
  
  /**
   * SBPreferences object to store all preferences for this class.
   */
  protected SBPreferences prefs = SBPreferences.getPreferencesFor(PathwayVisualizationOptions.class);
  
  /**
   * A graph on which operations are performed.
   */
  Graph2D graph;
  
  public Signal2PathwayTools(TranslatorPanel tp){
    this(tp.isGraphML()?(Graph2D) tp.getDocument():null);
  }
  
  public Signal2PathwayTools(Graph2D graph){
    super();
    this.graph=graph;
    if (this.graph==null) log.warning("Graph is null!");
  }
  
  /**
   * Write the signals into the node annotation list.
   * @param nsList
   */
  public <T extends NameAndSignals> void writeSignalsToNodes(Iterable<T> nsList) {
    writeSignalsToNodes(nsList, null,null);
  }
  /**
   * Write the signals into the node annotation list. 
   * @param nsList
   * @param experimentName
   * @param type
   */
  @SuppressWarnings("unchecked")
  public <T extends NameAndSignals> void writeSignalsToNodes(Iterable<T> nsList, String experimentName, SignalType type) {
    
    // Get GeneID 2 Node map
    TranslatorTools tools = new TranslatorTools(graph);
    Map<Integer, List<Node>> map = tools.getGeneID2NodeMap();
    
    // Write signals to nodes
    Map<ValuePair<String, SignalType>, NodeMap> signalMaps = new HashMap<ValuePair<String, SignalType>, NodeMap>();
    for (NameAndSignals ns : nsList) {
      // Get Node(s) for mRNA
      List<Node> node = getNodesForNameAndSignal(map, ns);
      
      // Get Signals
      if (node!=null && node.size()>0) {
        List<Signal> signals = getSignals(ns);
        
        // Add each signal to corresponding nodes.
        for (Signal sig: signals) {
          // Filter signals
          if ((experimentName==null || sig.getName().equals(experimentName)) &&
              (type==null || sig.getType().equals(type))) {
            // Get NodeMap for signal
            ValuePair<String, SignalType> key = new ValuePair<String, SignalType>(sig.getName(), sig.getType());
            NodeMap sigMap = signalMaps.get(key);
            if (sigMap==null) {
              sigMap = graph.createNodeMap();
              signalMaps.put(key, sigMap);
            }
            
            // Write signal to NodeMap
            for (Node n:node)  {
              sigMap.set(n, sig.getSignal().toString());
            }
          }
        }
      }
    }
    
    // Register signalMaps in graph
    GenericDataMap<DataMap, String> mapDescriptionMap = (GenericDataMap<DataMap, String>) graph.getDataProvider(KEGG2yGraph.mapDescription);
    for (Entry<ValuePair<String, SignalType>, NodeMap> signalSet : signalMaps.entrySet()) {
      String v = "["+signalSet.getKey().getB().toString() + "] " + signalSet.getKey().getA();
      mapDescriptionMap.set(signalSet.getValue(), v);
    }
  }

  @SuppressWarnings("rawtypes")
  private static List<Signal> getSignals(NameAndSignals ns) {
    List<Signal> signals = ns.getSignals();
    if (ns instanceof EnrichmentObject) {
      // In case of enrichment objects, get signals of source (mRNAs).
      Collection c = ((EnrichmentObject)ns).getGenesInClass();
      if (c!=null && c.size()>0 && c.iterator().next() instanceof NameAndSignals) {
        signals.clear();
        for (Object nas : c) {
          signals.addAll(((NameAndSignals)nas).getSignals());
        }
      }
    }
    return signals;
  }

  /**
   * @param map obtained from {@link TranslatorTools#getGeneID2NodeMap()}
   * @param ns
   * @return a list of all Nodes for a {@link NameAndSignals}.
   */
  private static List<Node> getNodesForNameAndSignal(Map<Integer, List<Node>> map,
    NameAndSignals ns) {
    List<Node> node = new LinkedList<Node>();
    Collection<Integer> gis = NameAndSignals.getGeneIds(ns);
    for (Integer gi : gis) {
      if (gi!=null && gi.intValue()>0) {
        Collection<Node> nList = map.get(gi);
        if (nList!=null) {
          node.addAll(nList);
        }
      }
    }
    return node;
  }

  /**
   * 
   * @param <T>
   * @param nsList list of {@link NameAndSignals}
   * @param sigMerge define how to merge non unique signals (e.g., defined by multiple probes)
   * @param experimentName for selecting the signal.
   * @param type of signal (e.g., fold change).
   * @param gradientColors OPTIONAL - set to null to read from preferences. Else:
   * list of colors for a gradient. Will be distributed equally, linear on the signal sample range.
   */
  public <T extends NameAndSignals> void colorNodesAccordingToSignals(Iterable<T> nsList, MergeType sigMerge, String experimentName, SignalType type, Color... gradientColors) {
    Color colorForUnaffectedNodes = PathwayVisualizationOptions.COLOR_FOR_NO_VALUE.getValue(prefs);
    
    // Get defaults
    if (gradientColors==null || gradientColors.length<1) {
      // Load default from config (e.g., blue-white-red).
      gradientColors = new Color[]{PathwayVisualizationOptions.COLOR_FOR_MINIMUM_FOLD_CHANGE.getValue(prefs),
          PathwayVisualizationOptions.COLOR_FOR_NO_FOLD_CHANGE.getValue(prefs),
          PathwayVisualizationOptions.COLOR_FOR_MAXIMUM_FOLD_CHANGE.getValue(prefs)};
    }

    // Get GeneID 2 Node map
    TranslatorTools tools = new TranslatorTools(graph);
    Map<Integer, List<Node>> map = tools.getGeneID2NodeMap();
    
    // Get min max signals for determin logarithmized (negative) fcs or not.
    double[] minMax = NameAndSignals.getMinMaxSignalGlobal(nsList, experimentName, type);
    // Make a symmetric min and max (-3 to +3) instead of -2.9 to + 3.2 because of better coloring then.
    Float maxFC = PathwayVisualizationOptions.FOLD_CHANGE_FOR_MAXIMUM_COLOR.getValue(prefs);
    double minFCthreshold = minMax[0]<0?(maxFC*-1):1/maxFC.doubleValue();
    minMax = new double[]{minFCthreshold,maxFC.doubleValue()};
    
    // Initiate color rescaler
    LinearRescale lred = new LinearRescale(minMax[0], minMax[1], getColorChannel(0, gradientColors));
    LinearRescale lgreen = new LinearRescale(minMax[0], minMax[1], getColorChannel(1, gradientColors));
    LinearRescale lblue = new LinearRescale(minMax[0], minMax[1], getColorChannel(2, gradientColors));
    
    Set<Node> allNodes = new HashSet<Node>(Arrays.asList(graph.getNodeArray()));
    for (NameAndSignals ns : nsList) {
      // Get Node(s) for mRNA
      List<Node> node = getNodesForNameAndSignal(map, ns);
      
      // Get Signals
      if (node!=null && node.size()>0) {
        List<Signal> signals = getSignals(ns);
        Collection<Signal> unique = Signal.merge(signals, sigMerge);
        for (Signal sig: unique) {
          if (sig.getName().equals(experimentName) && sig.getType().equals(type)) {
            Number v = sig.getSignal();
            if (v!=null && (!(v instanceof Double ) || !Double.isNaN((Double)v))) {
              double d = v.doubleValue();
              Color newColor = new Color(rescaleColorPart(lred, d),rescaleColorPart(lgreen, d),rescaleColorPart(lblue, d));
              for (Node n: node) {
                graph.getRealizer(n).setFillColor(newColor);
                allNodes.remove(n);
              }
            }
          }
        }
        
      }
    }
    
    // Set unaffected color for all other nodes but reference nodes.
    for (Node n: allNodes) {
      if (TranslatorTools.getKeggIDs(n).toLowerCase().trim().startsWith("path:")) continue;
      graph.getRealizer(n).setFillColor(colorForUnaffectedNodes);
    }
  }
  
  /**
   * Returns a value between 0 and 255.
   * @param lcolor configured rescaler for the color
   * @param oldValue old experimental value (e.g., fold change)
   * @return color code of the corresponding color part (r,g or b) of the new color.
   */
  public static int rescaleColorPart(LinearRescale lcolor, double oldValue) {
    return Math.max(0, Math.min(255, lcolor.rescale(oldValue).intValue()));
  }

  /**
   * @param i 0=red, 1=green, 2=blue
   * @param gradientColors list of colors
   * @return a list if the corresponding color-intensity in the given list of colors.
   */
  private List<Integer> getColorChannel(int i, Color... gradientColors) {
    List<Integer> red = new LinkedList<Integer>();
    for (Color c: gradientColors) {
      int f = 0;
      if (i==0) f = c.getRed();
      if (i==1) f = c.getGreen();
      if (i==2) f = c.getBlue();
      red.add(f);
    }
    
    return red;
  }

  /**
   * Batch create pathway pictures with visualized signals (nodes
   * colored accordings to signals).
   * @param observations <code>ValuePair<NameAndSignalsTab, ValuePair<String, SignalType>></code>
   * which uniquely describes the observations to take.
   * @param pathwayIDs reference pathway identifiers
   * identifier (organism unspecific).
   * @param outputFormat e.g., "graphml", "jpg", ... the output file extension!
   * @param outputDir
   */
  @SuppressWarnings("unchecked")
  public static void batchCreatePictures(
    final ValuePair<NameAndSignalsTab, ValuePair<String, SignalType>>[] observations,
    final String[] pathwayIDs, final String outputFormat, final File outputDir) {
    
    SwingWorker<Integer, Void> batchPicCreator = new SwingWorker<Integer, Void>() {
      @Override
      protected Integer doInBackground() throws Exception {

        // Prepare KEGGtranslator
        log.info("Batch creating pathway pictures...");
        KEGG2yGraph translator = (KEGG2yGraph) BatchKEGGtranslator.getTranslator(Format.GraphML, Translator.getManager());
        if (translator == null) {
          GUITools.showErrorMessage(null, "Could not instantiate KEGGtranslator.");
          return 0;
        }
        
        // Color every pathway and signal combination
        Map<String, Graph2D> pathwayCache = new HashMap<String, Graph2D>();
        AbstractProgressBar bar = IntegratorUI.getInstance().getStatusBar().showProgress();
        bar.setNumberOfTotalCalls(pathwayIDs.length*observations.length);
        int success=0;
        for (String pw : pathwayIDs) {
          String pwNumber = Utils.getNumberFromStringRevAsString(pw.length(), pw);
          for (ValuePair<NameAndSignalsTab, ValuePair<String, SignalType>> observation : observations) {
            bar.DisplayBar();
            
            try {
              // Get species and complete kegg pathway id.
              Species species = observation.getA().getSpecies();
              String keggAbbr = "ko";
              if (species!=null && species.getKeggAbbr()!=null) keggAbbr = species.getKeggAbbr();
              
              // Get pathway graph
              String pathwayID = keggAbbr+pwNumber;
              Graph2D graph = pathwayCache.get(pathwayID);
              if (graph==null) {
                String inputFile = KGMLSelectAndDownload.downloadPathway(pathwayID, false);
                if (inputFile==null) {
                  // Do not display error again for every observation.
                  bar.setCallNr(bar.getCallNumber()+(observations.length-1));
                  break;
                }
                try {
                  graph = (Graph2D) translator.translate(new File(inputFile));
                } catch (Throwable e) {
                  log.log(Level.SEVERE, "Could not translate graph for pathway " + pathwayID, e);
                  continue;
                }
                if (graph==null) {
                  log.severe("Could not get graph for pathway " + pathwayID);
                  continue;
                }
                pathwayCache.put(pathwayID, graph);
              }
              
              // Color nodes
              String obsExpName = observation.getB().getA();
              SignalType obsExpType = observation.getB().getB();
              Signal2PathwayTools instance = new Signal2PathwayTools(graph);
              instance.colorNodesAccordingToSignals((Iterable<? extends NameAndSignals>)observation.getA().getData(),
                MergeType.Mean, obsExpName, obsExpType, (Color[]) null);
              
              // Adjust title node
              Node n = TranslatorTools.getTitleNode(graph, pathwayID);
              double oldHeight = 0; String oldText = "";
              if (n!=null) {
                NodeRealizer nr = graph.getRealizer(n);
                oldHeight = nr.getHeight();
                nr.setHeight(oldHeight*2);
                nr.setY(nr.getY()-oldHeight);
                
                oldText = graph.getLabelText(n);
                graph.setLabelText(n, String.format("%s\n%s [%s]", oldText, obsExpName, obsExpType));
              }
              
              // Save graph.
              try {
                String outFile = Utils.ensureSlash(outputDir.getPath()) +
                StringUtil.removeAllNonFileSystemCharacters(
                  pathwayID + '.' + obsExpName + '.' + obsExpType + '.' + outputFormat);
                translator.writeToFile(graph, outFile, outputFormat);
                success++;
              } catch (Throwable e) {
                GUITools.showErrorMessage(null, e);
              }
              
              // Revert modifications for usage with next observation
              if (n!=null) {
                NodeRealizer nr = graph.getRealizer(n);
                nr.setHeight(oldHeight);
                nr.setY(nr.getY()+oldHeight);
                graph.setLabelText(n, oldText);
              }
            } catch (Throwable t) {
              t.printStackTrace();
              GUITools.showErrorMessage(null, t);
            }
          }
        }
        return success;
      }
      
      @Override
      protected void done() {
        super.done();
        Integer success;
        try {
          success = get();
        } catch (Exception e) {
          success=0;
        }
        String successMessage = String.format("Created %s pathway pictures.", success);
        log.info(successMessage);
        if (success>0) {
          GUITools.showMessage(successMessage, IntegratorUI.appName);
        }
        IntegratorUI.getInstance().getStatusBar().reset();
      }
      
    };
    batchPicCreator.execute();

  }
  
}