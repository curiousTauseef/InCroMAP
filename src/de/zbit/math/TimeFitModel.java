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
 * Copyright (C) 2011 by the University of Tuebingen, Germany.
 *
 * Integrator is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation. A copy of the license
 * agreement is provided in the file named "LICENSE.txt" included with
 * this software distribution and also available online as
 * <http://www.gnu.org/licenses/lgpl-3.0-standalone.html>.
 * ---------------------------------------------------------------------
 */

package de.zbit.math;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import de.zbit.data.Signal.SignalType;
import de.zbit.data.mRNA.mRNATimeSeries;
import de.zbit.util.objectwrapper.ValueTriplet;

/**
 * This class uses the result of the TimeFit algorithm to provide a continous representation
 * of time series data.
 * See: Bar-Joseph, Z. et al. (2003).
 * 			Continuous representations of time-series gene expression data.
 * 			Journal of Computational Biology.
 *
 * @see <a href="http://www.ncbi.nlm.nih.gov/pubmed/12935332">The PubMed entry of the paper</a>
 * 
 * @author Felix Bartusch
 * @version
 */

public class TimeFitModel extends TimeSeriesModel {

	//RealMatrix s;
	ArrayList<Point2D> controlPoints;
	double[] knots;
	RealMatrix mu;
	RealMatrix gamma;
	double[] noise;
	
	@Override
	public void generateModel(mRNATimeSeries dataPoints,
			List<ValueTriplet<Double, String, SignalType>> timePoints,
			double cutoff, boolean isExponentiallyDistributed) {
		// Use another method to generate the model.
		return;
	}
	
	
	public void generateModel(mRNATimeSeries dataPoints,
			RealMatrix mu, RealMatrix gamma,
			ArrayList<Point2D> controlPoints, double[] knots,
			List<ValueTriplet<Double, String, SignalType>> timePoints) {
		
		// The model was already generated by @link{de.zbit.math.TimeFit}, so
		// just set the pre-computed objects.
		numDataPoints = dataPoints.getNumberOfSignals();
		this.mu = mu;
		this.gamma = gamma;
		this.controlPoints = controlPoints;
		this.knots = knots;
		
		// Get the points (x, f(x))
		this.x = new double[numDataPoints];
		this.y = new double[numDataPoints];
				
		for(int i=0; i<numDataPoints; i++) {
			x[i] = timePoints.get(i).getA();			// the i-th timePoints
			y[i] = Double.valueOf(dataPoints.getSignalValue(timePoints.get(i).getC(), timePoints.get(i).getB()).toString());
		}
		
		return;
	}
	
	
	@Override
	public double computeValueAtTimePoint(double timePoint) {
		// The spline basis function at time point t
		RealMatrix s = computeSplineBasisFunctions(timePoint);
		
		// Return the modeled value as described in the paper.
		// testing
		//System.out.println(this.name + " returned " + (s.multiply(mu.add(gamma)).getEntry(0, 0)));
		if(timePoint == 1.0) {
			System.out.println("s(t): " + s.getRowDimension() + "x" + s.getColumnDimension());
			System.out.println(s);
			System.out.println("mu: " + mu.getRowDimension() + "x" + mu.getColumnDimension());
			System.out.println(mu);
			System.out.println("gamma: " + mu.getRowDimension() + "x" + gamma.getColumnDimension());
			System.out.println(gamma);
		}
		return s.multiply(mu.add(gamma)).getEntry(0, 0);
	}
	
	
	/**
	 * The Cox-deBoor recursion formula to calculate the normalized B-Spline basis.
	 * 
	 * @param i number of the basis spline
	 * @param k the order of the basis polynomial (i.e. k=4 for a cubic polynomial)
	 * @param t the argument for the basis spline
	 * @return
	 */
	public double normalizedBSplineBasis(int i, int k, double t) {
		// The base case of the recursion
		if(k == 1) {
			if((knots[i] <= t) && (t < knots[i+1]))
				return 1;
			else
				return 0;
		}

		// The two recursive calls: rec1 = b_{i,k-1}(t), rec2 = b_{i+1,k-1}(t)
		double rec1 = normalizedBSplineBasis(i, k-1, t);
		double rec2 = normalizedBSplineBasis(i+1, k-1, t);

		return ((t-knots[i])*rec1)/(knots[i+k-1]-knots[i]) + ((knots[i+k]-t)*rec2)/(knots[i+k]-knots[i+1]);
	}
	
	

	/**
	 * Compute the value of the spline basis functions at the control points at a certain time point.
	 * @param t
	 * @return
	 */
	private RealMatrix computeSplineBasisFunctions(double t) {

		// The value of the spline basis functions evaluated at time point t. A q x 1 vector.
		RealMatrix res = new Array2DRowRealMatrix(1, controlPoints.size());
		for(int i=0; i<controlPoints.size(); i++) {
			// i is the number of the spline basis function, 4 is the order of the spline basis function
			// t is the time
			res.setEntry(0, i, normalizedBSplineBasis(i, 4, t));	
		}
		return res;
	}
	
	/**
	 * Get n values.
	 */
}