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
package de.zbit.integrator;

import java.util.Collection;
import java.util.Map;

import de.zbit.data.NameAndSignals;
import de.zbit.data.VisualizedData;
import de.zbit.graph.io.def.GraphMLmaps;
import de.zbit.util.objectwrapper.ValueTriplet;

/**
 * Extended maps for GraphML graphs. See {@link GraphMLmaps}.
 * @author Clemens Wrzodek
 * @version $Rev$
 */
public interface GraphMLmapsExtended extends GraphMLmaps {
  /*
   * Remark: a String starting with "_" is hidden in the
   * Observation table when clicked on a node.
   */
  
  /**
   * True for all nodes, that are cloned to have multiple
   * variants for the same node (e.g., to color nodes
   * according to probes, NOT gene-centered).
   * <p>False for all original nodes and
   * <p><code>NULL</code> for all nodes, that have not
   * been cloned.
   */
  public final static String NODE_IS_COPY = "_isVariant";
  
  /**
   * True for all nodes, that have been added to visualize
   * microRNA target interactions in the graph.
   */
  public static final String NODE_IS_MIRNA = "_isMiRNA";
  
  /**
   * If this node has been created for a {@link NameAndSignals},
   * this map stores the instance that corresponds to this node.
   */
  public static final String NODE_NAME_AND_SIGNALS = "_NameAndSignals";
  
  /**
   * {@link Map} from any {@link VisualizedData}
   * that maps to a {@link Collection} of actual raw (not gene-centered)
   * NameAndSignals matching to this node.
   */
  public static final String NODE_VISUALIZED_RAW_NS = "_RawNameAndSignals";

  /**
   * If this node has been colored for a specific signal/observation,
   * this KEY points to a {@link ValueTriplet}, describing this signal
   * with a)tab/filename b)experimentName c)SignalType.
   */
  public static final String NODE_BELONGS_TO = "_VisualizedSignal";

  /**
   * Stores the number of children (ONLY FOR GROUP/FOLDER NODES)
   * this node has. The stored value is an {@link Integer}.
   */
  public static final String NODE_NUMBER_OF_CHILDREN = "ChildNodes";

  /**
   * Maps a Compound node to an InChIKey identifier. Note: This is NOT naturally
   * available from KEGGtranslator and must be established manually!
   */
  public static final String NODE_COMPOUND_ID = "COMPOUND_ID";
  
}
