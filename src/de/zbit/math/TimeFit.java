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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.stat.correlation.Covariance;

import de.zbit.data.Signal;
import de.zbit.data.Signal.SignalType;
import de.zbit.data.mRNA.mRNATimeSeries;
import de.zbit.util.objectwrapper.ValueTriplet;

/**
 * Implementation of the TimeFit algorithm to compute a continous representation
 * of time series data. This class just computes the parameters of the single gene models.
 * The computed parameters are later used to define the model for each single gene.
 * So this class does not implement the @link{de.zbit.math.TimeFit.computeValueAtTimePoint(double)} method.
 * See: Bar-Joseph, Z. et al. (2003).
 * 			Continuous representations of time-series gene expression data.
 * 			Journal of Computational Biology.
 *
 * @see <a href="http://www.ncbi.nlm.nih.gov/pubmed/12935332">The PubMed entry of the paper</a>
 * 
 * @author Felix Bartusch
 * @version $Rev$
 */
public class TimeFit extends TimeSeriesModel {
	/**
	 * Order of the basis polynomials (i.e. for cubic polynomial order = 4)
	 */
	int order = 4;

	/**
	 * The number of the spline control points.
	 */
	int q = -1;

	/**
	 * The control points of the spline
	 */
	ArrayList<Point2D> controlPoints;

	/**
	 * The columns of the expression matrix which belong to the control points
	 */
	int[] controlPointsColumn;

	/**
	 * The values of the basis B-splines at the control points.
	 * A m by q matrix, where m is the number of time points and q
	 * is the number of control points. The first row correspondends to
	 * the value of the basis B-splines for the first time point.
	 */
	RealMatrix s;

	/**
	 * The result of transposed s multiplied with s. Because it is used many times. 
	 */
	RealMatrix sts;

	/**
	 * The measured data for ALL genes.
	 * A row corresponds to a measured time points, a columns corresponds to the data of one gene.
	 * To get the data of gene i as m x 1 array, use yMatrix.getColumnMatrix(i).
	 */
	RealMatrix yMatrix;

	/**
	 * The class centers. The center class i is given the i-th column of the mu matrix.
	 */
	RealMatrix mu;

	/**
	 * The mapping of a gene to his class.
	 */
	int[] gene2class;

	/**
	 * The set of genes for a class.
	 */
	ArrayList<ArrayList<Integer>> class2genes;

	/**
	 * The covariance matrices Γ_j of the classes.
	 */
	ArrayList<RealMatrix> covMatrices;

	/**
	 * The list of the inverse covariance matrices.
	 */
	ArrayList<RealMatrix> inverseCovMatrices;

	/**
	 * The Gene specific variation coefficients. The j-nth matrix of the list contains the  variation
	 * coefficients for the j-th class.
	 * ...
	 * The rows of a matrix corresponds to genes and the columns
	 * corresponds to the variation coefficient of the control points. So a matrix has a dimension of
	 * numGenes by q.
	 */
	ArrayList<RealMatrix> gamma;


	/**
	 * The variances of the genes.
	 */
	double[] geneVariances;

	/**
	 * The variance.
	 */
	double variance = 1;

	/**
	 * The noise vectors for each gene. The noise vector for gene i is the i-th row of the matrix.
	 */
	RealMatrix noiseVectors;

	/**
	 * The knot vector of the B-spline. The number of knots has to fulfill the following equation
	 * #knots = q + order
	 */
	double[] knots;

	/**
	 * The 	probability that gene i belongs to class j, where row correspondens to genes and columns
	 * to classes. So probs[i][j] contains the probability, that gene i belongs to class j.
	 */
	double[][] probs;
	
	/**
	 * Threshold for the EM-algorithm. Tells EM-algorithm when to stop.
	 */
	double threshold = 0.005;
	
	/**
	 * Maximum number of iteration for the EM-algorithm.
	 */
	int maxIteration = 15;

	/**
	 * The class probability that are updated in the EM-algorithm.
	 */
	double[] classProbs;

	/**
	 * The log likelihood of the data given the model.
	 */
	double logLikelihood;

	/**
	 * The number of gene classes.
	 */
	int numClasses;

	/**
	 * The number of genes.
	 */
	int numGenes;

	/* (non-Javadoc)
	 * @see de.zbit.math.TimeSeriesModel#generateModel(de.zbit.data.mRNA.mRNATimeSeries, java.util.List)
	 */
	@Override
	public void generateModel(mRNATimeSeries dataPoints,
			List<ValueTriplet<Double, String, SignalType>> timePoints) {
		// The model isn't build for one single gene but for all genes at the same time.
		// intentially left blank!
		return;
	}

	/**
	 * Generate the time series model for the data.
	 * @param data
	 * @param timePoints
	 */
	public void generateModel(ArrayList<mRNATimeSeries> data,
			List<ValueTriplet<Double, String, SignalType>> timePoints) {

		// TODO Log the actions, so that the source of exceptions can be located better
		// TODO Write JavaDoc comments for each method
		
		// Initialize all fields, so that the model can be computed
		init(data, timePoints);

		// run the EM-algorithm
		logLikelihood = 2;
		double oldLogLikelihood = 1;

		int iteration = 0;
		while(logLikelihood / oldLogLikelihood < 1 - threshold || iteration < maxIteration) {
			// increase counter
			iteration++;
			// Save the current logLikelihood
			oldLogLikelihood = logLikelihood;
			
			/*
			 * E-Step:
			 * 
			 * For all genes i and classes j, compute P(j|i). This is the probability, that gene i
			 * belongs to class j.
			 */ 
			computeProbabilities();

			/*
			 * M-Step
			 * 
			 * For all genes i and classes j, find the MAP estimate of gamma_ij
			 */
			findMAPEstimate();

			// Maximize the covariance matrices Γ, the variance and the class centers mu
			maximizeVariance();

			// Compute the new class centers mu. To do this, we have to compute two matrices and
			// multiplicate them
			maximizeMu();

			// Compute the new class covariance matrices.
			maximizeCovMatrices();

			// Update the class probabilities
			updateClassProbs();

			// Compute the new log likelihood.
			computeLogLikelihood();

			System.out.println("Fraction: " + (logLikelihood / oldLogLikelihood) + " Threshold: " + (1 - threshold));
		}
		
		// Finally assign to each gene the corresponding class
		assignClassToGenes();
	}
	
	
	/**
	 * 
	 * @param dataPoints
	 * @param timePoints
	 * @param gene
	 * @return
	 */
	public TimeFitModel getGeneModel(mRNATimeSeries dataPoints,
			List<ValueTriplet<Double, String, SignalType>> timePoints,
			int gene) {
		TimeFitModel m = new TimeFitModel();
		
		// To which class belongs the gene?
		int classOfGene = gene2class[gene];
		
		// Generate the model with the parameters provided by the class of the gene
		m.generateModel(dataPoints, mu.getColumnMatrix(classOfGene), gamma.get(classOfGene).getColumnMatrix(gene),
				controlPoints, knots, timePoints);
		
		return m;
	}


	/* (non-Javadoc)
	 * @see de.zbit.math.TimeSeriesModel#computeValueAtTimePoint(double)
	 */
	@Override
	public double computeValueAtTimePoint(double timePoint) {
		// Intentially left blank
		return 0;
	}

	/* (non-Javadoc)
	 * @see de.zbit.math.TimeSeriesModel#init(java.util.Collection)
	 */
	@Override
	public void init(List<mRNATimeSeries> data,
			List<ValueTriplet<Double, String, SignalType>> timePoints) {

		// Generate a matrix from the mRNATimeSeries data
		yMatrix = timeSeries2Matrix(data);
		// testing
		System.out.println("yMatrix Rows: " + yMatrix.getRowDimension() + "\tCols: " + yMatrix.getColumnDimension());


		// How many time points and genes do we have?
		numGenes = yMatrix.getColumnDimension();
		numDataPoints = yMatrix.getRowDimension();

		// Set the time points of the experiments
		x = new double[numDataPoints];
		for(int i=0; i<numDataPoints; i++) {
			x[i] = timePoints.get(i).getA();
		}

		// Place the control points
		chooseControlPoints(x);

		// Place the knots
		placeKnots();
		// testing
		for(int i=0; i<knots.length; i++) {
			System.out.println("Knot " + i + ": " + knots[i]);
		}

		// Compute the values of the basis B-splines
		// S, where S_[ij] = b_{j,4}(t_i)
		computeS();

		// 1. Initialize the class center for each class as described in the paper.
		// The center of a class is the mean value of the spline coefficients of genes in the class.
		initializeClassCenter();
		// testing
		System.out.println("Initial class centers: " + mu);

		// Initialize mapping from a class to the set of genes in the class.
		initializeGenes2ClassMapping();

		// Compute the covariance matrix for each class
		initializeCovMatrices();

		// Initialize the inverse covariance matrices.
		computeInverseCovMatrices();	

		// Sample the gene specific variation coefficients. Each gene has q (number of control points)
		// variation coefficients, which are sampled with the class covariance matrix.
		computeGamma();

		// Initialize the gene variances and sample the noise vector for each gene.
		computeGeneVariances();

		// Sample the noise vector for each gene
		sampleNoiseVectors();

		// Now find the best parameters and class assignment with an EM-algorithm
		computeClassProbs();

		setInitialized(true);
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
	 * Transcribe the measured data from mRNATimeSeries to a simple Array of doubles.
	 * This is needed, because most of the computation involves multiplying and adding matrices.
	 */
	private RealMatrix timeSeries2Matrix(List<mRNATimeSeries> data) {
		// How many genes and how many experiments do we have?
		int numGenes = data.size();
		int numExperiments = data.get(0).getNumberOfSignals();
		double[][] res = new double[numExperiments][numGenes];

		// Transcribe the given data into the matrix
		for(int i=0; i<numGenes; i++) {
			mRNATimeSeries m = data.get(i);
			List<Signal> signals = m.getSignals();
			for(int j=0; j<numExperiments; j++) {
				res[j][i] = signals.get(j).getSignal().doubleValue();
			}
		}

		return new Array2DRowRealMatrix(res);
	}


	/**
	 * Choose the x-values of the control points used for the model.
	 * The method tries to choose the control points so, that they are roughly equidistant.
	 * Also keep track, to which column of the expression matrix the conrol points belong.
	 * @param timePoints The time points of the available data in ascending order.
	 */
	private void chooseControlPoints(double[] timePoints) {
		// A simple estimation for the number of control points.
		this.q = (timePoints.length / 4)+1;

		// This is the best case we try to achieve: equidistant control points
		double[] equidistantPoints = new double[q]; // Best x-values of the control points
		double distance = getLastTimePoint() - getFirstTimePoint();
		equidistantPoints[0] = getFirstTimePoint();
		for(int i=1; i<q-1; i++	) {
			equidistantPoints[i] = i * (getFirstTimePoint() + distance / (q));
		}
		equidistantPoints[q-1] = getLastTimePoint();

		// Keep track of the columns of the expression matrix belonging to the control points.
		controlPointsColumn = new int[q];
		controlPointsColumn[0] = 0;

		// Select q control points, so that they are roughly equidistant
		controlPoints = new ArrayList<Point2D>(q);
		controlPoints.add(new Point2D.Double(getFirstTimePoint(), 0)); // we're not interested in the y-value yet
		// The first and last of the q control point is given, so choose the q-2 other
		// control points.
		for(int i=1; i<q-1; i++) {
			// Search the given timepoints for the time point with the minimal distance
			// to i-th equidistantPoint
			double oldDistance = Double.MAX_VALUE;
			int lastIndex = 1; // The index of the last timePoint, so we don't choose two similar timePoints
			for(int j=lastIndex; j < timePoints.length-1; j++) {
				distance = Math.abs(timePoints[j] - equidistantPoints[i]);
				if(distance < oldDistance)
					oldDistance = distance; // Found a better timepoint					
				else {
					lastIndex = j;
					controlPoints.add(new Point2D.Double(x[j-1], 0)); // The previous point was better
					controlPointsColumn[i] = j-1; // column of the control point in the expression matrix
					break;
				}
			}
		}
		controlPoints.add(new Point2D.Double(getLastTimePoint(), 0));
		controlPointsColumn[q-1] = numDataPoints-1;

		// for testing
		for(int i=0; i<q; i++) {
			System.out.println("Control Point " + i + "\t opt: " + equidistantPoints[i] + "\t\t\tchosen: " + controlPoints.get(i).getX());
		}
	}


	/**
	 * 
	 */
	private void computeS() {
		double[][] s_matrix = new double[numDataPoints][q];

		for(int i=0; i<numDataPoints; i++) {
			for(int j=0; j<q; j++) {
				s_matrix[i][j] = normalizedBSplineBasis(j,4,i);
			}
		}
		s = new Array2DRowRealMatrix(s_matrix);

		// S_transposed * S
		sts = s.transpose().multiply(s);
	}


	/**
	 * Place the knots equidistant between the first and last time point.
	 * There are {@link #q} + {@link #order} knots.
	 */
	private void placeKnots() {
		// How many knots have to be placed?
		int numberOfKnots = q + order;
		System.out.println(q);
		knots = new double[numberOfKnots];

		// Place the knots. Two knots before the first time point and two knots after the
		// last time point are needed to describe the resulting model in the interval
		// [firstTimePoint, lastTimePoint]
		knots[2] = getFirstTimePoint();
		knots[numberOfKnots-3] = getLastTimePoint();
		double stepsize = (getLastTimePoint() - getFirstTimePoint()) / (numberOfKnots-5);
		for(int i=-2; i<numberOfKnots-2; i++)
			knots[i+2] = getFirstTimePoint() + stepsize * i;
	}


	/**
	 * Initialize the class centers with a random chosen gene.
	 */
	private void initializeClassCenter() {
		this.mu = new Array2DRowRealMatrix(q, numClasses);
		// The already chosen class centers. Don't allow a two same class centers.
		ArrayList<Integer> chosenGenes = new ArrayList<Integer>(numClasses);
		// Initialize the class centers 
		int i;  // number of gene
		for(int j=0; j<numClasses; j++) {
			// Choose a new random gene.
			i = (int) (Math.random() * numGenes);
			while (chosenGenes.contains(i)) {
				i = (int) (Math.random() * numGenes);
			}
			// Compute the class center
			RealMatrix m = s.transpose().multiply(s); // An intermediary result
			// Invert m
			m = new SingularValueDecomposition(m).getSolver().getInverse();
			// Compute the final initial class center, a q by 1 vector
			m = m.multiply(s.transpose()).multiply(yMatrix.getColumnMatrix(i));
			// Add the class center to the list of class centers
			mu.setColumnMatrix(j, m); // the i-th center is in the i-th column of mu
		}
	}


	/**
	 * Select a class for each gene at random and create mappings from genes2class and the
	 * other way round.
	 */
	private void initializeGenes2ClassMapping() {
		class2genes = new ArrayList<>(numClasses);
		for(int i=0; i<numClasses; i++) {
			class2genes.add(new ArrayList<Integer>());
		}
		// Initialize the mapping from a gene to its class
		gene2class = new int[numGenes];

		// For each gene, select a class j uniformly at random.
		int j;
		for(int i=0; i<numGenes; i++) {
			j = (int) (Math.random() * numClasses); // Select a random class j for gene i
			gene2class[i] = j;
			// Add the gene to the class2gene mapping
			class2genes.get(j).add(i);
		}
	}


	/**
	 * Initialize the covariance matrix for each class.
	 */
	private void initializeCovMatrices() {
		// This array is used to index the rows of the expression values for the submatrix
		int[] colsPrimitive;

		covMatrices = new ArrayList<RealMatrix>(numClasses);
		RealMatrix subMatrix;
		System.out.println("Rows: " + yMatrix.getRowDimension() + "Columns: " + yMatrix.getColumnDimension());
		for(int j=0; j<numClasses; j++) {
			// Get a submatrix with the expression values of genes of class j
			Integer[] cols = new Integer[class2genes.get(j).size()];
			colsPrimitive = ArrayUtils.toPrimitive(class2genes.get(j).toArray(cols));
			subMatrix = yMatrix.getSubMatrix(controlPointsColumn, colsPrimitive);

			// Compute and set the covariance matrix for class j
			Covariance cov = new Covariance(subMatrix.transpose());
			covMatrices.add(cov.getCovarianceMatrix());
		}
	}


	/**
	 * Compute the inverse covariance matrices of @link{de.zbit.math.TimeFit.covMatrices}.
	 */
	private void computeInverseCovMatrices() {
		inverseCovMatrices = new ArrayList<RealMatrix>(numClasses);
		for(int j=0; j<numClasses; j++) {
			inverseCovMatrices.add(new SingularValueDecomposition(covMatrices.get(j)).getSolver().getInverse());
		}	
	}


	/**
	 * 
	 */
	private void computeGamma() {
		gamma = new ArrayList<RealMatrix>(numClasses);
		MultivariateNormalDistribution dist;
		// Iterate over the classes and sample matrix of the variation coefficients
		for(int j=0; j<numClasses; j++) {
			// The distribution of the variation coefficients for class j
			dist = new MultivariateNormalDistribution(new double[q], covMatrices.get(j).getData());
			// The matrix for the variation coefficients of class j
			RealMatrix m = new Array2DRowRealMatrix(q, numGenes);
			// Fill the matrix with sample values
			for(int i=0; i<numGenes; i++) {
				m.setColumn(i, dist.sample());				
			}
			gamma.add(m);
		}		
	}


	/**
	 * 
	 */
	private void computeGeneVariances() {
		geneVariances = new double[numGenes];
		for(int i=0; i<numGenes; i++) {
			geneVariances[i] = MathUtils.variance(yMatrix.getColumnMatrix(i).getSubMatrix(controlPointsColumn, new int[1]).getColumn(0)); 
		}
		System.out.println("Variance: \t" + geneVariances[1]);	
	}


	/**
	 * 
	 */
	private void sampleNoiseVectors() {
		noiseVectors = new Array2DRowRealMatrix(numGenes, q);
		Random rand = new Random();
		for(int i=0; i<numGenes; i++) {
			for(int j=0; j<q; j++) {
				noiseVectors.setEntry(i, j, rand.nextGaussian() * geneVariances[i]);
			}
		}
		System.out.println("Noise vector: \t" + noiseVectors.getRowMatrix(1).toString()); // Testing output
	}


	/**
	 * Compute the class probabilities. That is the initial probability that a gene i belongs to class j.
	 */
	private void computeClassProbs() {
		// At the beginning, each class has the same probability
		double p = 1.0 / numClasses;
		classProbs = new double[numClasses];
		for(int j=0; j<numClasses; j++) {
			classProbs[j] = p;
		}
	}


	/**
	 * Compute the power of a BigDecimal to an double value.
	 */
	private BigDecimal bigDecimalPow(BigDecimal n1, BigDecimal n2) {
		// Perform X^(A+B)=X^A*X^B (B = remainder)
		// see: http://stackoverflow.com/questions/3579779/how-to-do-a-fractional-power-on-bigdecimal-in-java
		int signOf2 = n2.signum();
		double dn1 = n1.doubleValue();
		// Compare the same row of digits according to context
		n2 = n2.multiply(new BigDecimal(signOf2)); // n2 is now positive
		BigDecimal remainderOf2 = n2.remainder(BigDecimal.ONE);
		BigDecimal n2IntPart = n2.subtract(remainderOf2);
		// Calculate big part of the power using context -
		BigDecimal intPow = n1.pow(n2IntPart.intValueExact());
		BigDecimal doublePow = new BigDecimal(Math.pow(dn1, remainderOf2.doubleValue()));
		BigDecimal result = intPow.multiply(doublePow);

		return result;
	}


	/**
	 * 
	 * @param bigE
	 * @param e1
	 * @param e2
	 * @return
	 */
	private BigDecimal computeFactorAsBigDecimal(BigDecimal bigE, double e1,
			double e2, double classProb) {
		BigDecimal bigF1 = bigDecimalPow(bigE, new BigDecimal(e1));
		BigDecimal bigF2 = bigDecimalPow(bigE, new BigDecimal(e2));
		return new BigDecimal(classProb).multiply(bigF1).multiply(bigF2);
	}


	/**
	 * 
	 */
	private void computeProbabilities() {
		// For all genes i and classes j, compute P(j|i). This is the probability, that gene i
		// belongs to class j.
		probs = new double[numGenes][numClasses];
		double[] factors = new double[numClasses];
		double sumFactors = 0;
		double e1;
		double e2;
		boolean isFactorInfinite = false;
		BigDecimal[] bigFactors = new BigDecimal[numClasses];
		BigDecimal bigE = new BigDecimal(Math.exp(1));  // euler constant
		BigDecimal bigSumFactors = new BigDecimal(1);
		for(int i=0; i<numGenes; i++) {
			//System.out.println("Compute prob for gene: " + i);
			// Compute the factors uses for this gene once
			for(int j = 0; j<numClasses; j++) {
				// The factors
				RealMatrix m1 = yMatrix.getColumnMatrix(i).subtract(s.multiply((mu.getColumnMatrix(j).add(gamma.get(j).getColumnMatrix(i)))));
				e1 = -(m1.transpose().multiply(m1).getEntry(0, 0) / variance);
				e2 = -0.5 * (gamma.get(j).getColumnMatrix(i).transpose().multiply(inverseCovMatrices.get(j)).multiply(gamma.get(j).getColumnMatrix(i)).getEntry(0, 0));
				factors[j] = classProbs[j] * Math.exp(e1) * Math.exp(e2);
				// Check if we need BigDecimals
				if(Double.isInfinite(factors[j])) {
					isFactorInfinite = true;
					// Copy the already computed factors
					for(int k=0; k<j; k++) {
						bigFactors[k] = new BigDecimal(factors[k]);
					}
					// Compute the big factor with BigDecimals
					bigFactors[j] = computeFactorAsBigDecimal(bigE, e1, e2, classProbs[j]);

				} else if(isFactorInfinite) {
					// There was an infinite factor, so compute the following factors as BigDecimal
					bigFactors[j] = computeFactorAsBigDecimal(bigE, e1, e2, classProbs[j]);
				}
			}

			// Compute the sum of the factors
			if(isFactorInfinite) {
				bigSumFactors = de.zbit.util.ArrayUtils.sum(bigFactors);				
			} else {
				sumFactors = de.zbit.util.ArrayUtils.sum(factors);
			}

			// Check whether the sum of the factors is infinite
			if(Double.isInfinite(sumFactors)) {
				isFactorInfinite = true;
				// Save the factors as BigDecimals
				for(int j=0; j<numClasses; j++) {
					bigFactors[j] = new BigDecimal(factors[j]); 
				}
				// Compute the sum of the bigFactors
				bigSumFactors = de.zbit.util.ArrayUtils.sum(bigFactors);
			}

			// Use the computed factors to compute P(j|i)
			for (int j = 0; j<numClasses; j++) {
				if(isFactorInfinite) {
					probs[i][j] = (bigFactors[j].divide(bigSumFactors, 5)).doubleValue();					
				} else {
					probs[i][j] = factors[j] / sumFactors;	
				}

				if(Double.isInfinite(probs[i][j]) || Double.isNaN(probs[i][j])) {
					System.out.println("Invalid probability");
				}
			}
			isFactorInfinite = false;
		}
	}


	/**
	 * 
	 */
	private void findMAPEstimate() {
		RealMatrix m1;		// a factor for the MAP estimate, computed once for each class
		for(int j=0; j<numClasses; j++) {
			m1 = inverseCovMatrices.get(j).scalarMultiply(variance).add((s.transpose().multiply(s)));
			m1 = new SingularValueDecomposition(m1).getSolver().getInverse();
			// The gamma matrix of class j
			RealMatrix new_gamma = gamma.get(j);
			// Compute the MAP estimate of gamma_ij for each gene
			for(int i=0; i<numGenes; i++) {
				RealMatrix m2 = s.transpose().multiply((yMatrix.getColumnMatrix(i).subtract(s.multiply(mu.getColumnMatrix(j)))));
				new_gamma.setColumnMatrix(i, m1.multiply(m2));
			}
			// Set the new gamma matrix for class j
			gamma.set(j, new_gamma);
		}
	}


	/**
	 * 
	 */
	private void maximizeVariance() {
		// Assume that n_i in the paper is the number of experiments (measured points) for gene i
		// Because here all genes have the same number of experiments
		int n = numGenes * numDataPoints;
		// The traces of some specific matrices are needed. The j-th value represents the trace computed
		// with the use of j-th inverted covariance matrix
		double[] traces = new double[numClasses];
		for(int j=0; j<numClasses; j++) {
			traces[j] = (new SingularValueDecomposition(inverseCovMatrices.get(j).add(sts)).getSolver()).getInverse().add(sts).getTrace();
		}

		// For the variance, we have to compute a sum over genes and classes
		double sum = 0;
		// Two factors used for better coed
		RealMatrix f1;
		RealMatrix f2;
		for(int i=0; i<numGenes; i++) {
			for(int j=0; j<numClasses; j++) {
				f1 = yMatrix.getColumnMatrix(i).subtract(s.multiply(mu.getColumnMatrix(j).add(gamma.get(j).getColumnMatrix(i)))).transpose();
				f2 = yMatrix.getColumnMatrix(i).subtract(s.multiply((mu.getColumnMatrix(j).add(gamma.get(j).getColumnMatrix(i).scalarAdd(traces[j])))));
				sum += probs[i][j] * (f1.multiply(f2).getEntry(0, 0));
			}
		}
		variance = sum / n;
	}


	/**
	 * 
	 */
	private void maximizeMu() {
		RealMatrix m1 = new Array2DRowRealMatrix(q, q);
		RealMatrix m2 = new Array2DRowRealMatrix(q, 1);
		for(int j=0; j<numClasses; j++) {
			// Each gene plays a role for the new class center.
			for(int i=0; i<numGenes; i++) {
				m1 = m1.add(sts.scalarMultiply(probs[i][j]));
				m2 = m2.add(s.transpose().scalarMultiply(probs[i][j]).multiply(yMatrix.getColumnMatrix(i).subtract(s.multiply(gamma.get(j).getColumnMatrix(i)))));
			}
			// m1 has to be inverted
			try{
				m1 = new SingularValueDecomposition(m1).getSolver().getInverse();
			} catch(Exception e) {
				System.out.println("mu is singular.");
				System.out.println(mu);
				System.exit(-1);
			}
			mu.setColumnMatrix(j, m1.multiply(m2));
		}
	}


	/**
	 * 
	 */
	private void maximizeCovMatrices() {
		// Compute at first summands that are used very often in the following part.
		RealMatrix m1;
		ArrayList<RealMatrix> summands = new ArrayList<RealMatrix>(numClasses);
		for(int j=0; j<numClasses; j++) {
			m1 = inverseCovMatrices.get(j).add(sts.scalarMultiply(1/variance));
			summands.add(new SingularValueDecomposition(m1).getSolver().getInverse());
		}		
		// Now compute the new covariance matrices
		RealMatrix numerator = new Array2DRowRealMatrix(q, q);
		double denominator = 0;
		RealMatrix m; // a factor
		for(int j=0; j<numClasses; j++) {
			// Build sum over the genes
			for(int i=0; i<numGenes; i++) {
				m = gamma.get(j).getColumnMatrix(i).multiply(gamma.get(j).getColumnMatrix(i).transpose());
				numerator = numerator.add(m.add(summands.get(j)).scalarMultiply(probs[i][j]));
				denominator += probs[i][j];
			}
			// The final result
			m = numerator.scalarMultiply(1/denominator);
			covMatrices.set(j, m);
			// Compute also the new inverse of the covMatrix
			inverseCovMatrices.set(j, new LUDecomposition(m).getSolver().getInverse());
		}
	}


	/**
	 * 
	 */
	private void updateClassProbs() {
		double sum = 0;
		for(int j=0; j<numClasses; j++) {
			sum = 0;
			for(int i=0; i<numGenes; i++) {
				sum += probs[i][j];
			}
			classProbs[j] = sum / numGenes;
		}
	}


	/**
	 * 
	 */
	private void computeLogLikelihood() {
		double newLogLikelihood = 0;
		double exp1 = 0; // The first exponent
		double exp2 = 0; // The second exponent
		// Square root of the covMatrix determinants are often needed
		double[] squareRootDets = new double[numClasses];
		for(int j=0; j<numClasses; j++) {
			squareRootDets[j] = Math.sqrt(new LUDecomposition(covMatrices.get(j)).getDeterminant());
		}
		// Now compute the log likelihood
		for(int i=0; i<numGenes; i++) {
			double sum = 0;
			// This indicator variable (dummy variable) assignes each gene to exactly one class.
			// So this is the class j for gene i with the highest probability.
			int j = findClassOfGene(probs[i]);
			//for(int j=0; j<numClasses; j++) {
			RealMatrix m = yMatrix.getColumnMatrix(i).subtract(s.multiply(mu.getColumnMatrix(j).add(gamma.get(j).getColumnMatrix(i))));
			exp1 = - (m.transpose().multiply(m).scalarMultiply(1/(2*variance)).getEntry(0, 0));
			exp2 = -0.5 * (gamma.get(j).getColumnMatrix(i).transpose().multiply(inverseCovMatrices.get(j)).multiply(gamma.get(j).getColumnMatrix(i)).getEntry(0, 0));
			if(Double.isNaN(exp1) || Double.isNaN(exp2) || Double.isInfinite(exp1) || Double.isInfinite(exp2)){
				System.out.println("--------");
				if (Double.isNaN(exp1))
					System.out.println("exp1 is NaN");
				if (Double.isNaN(exp2))
					System.out.println("exp2 is NaN");
				if (Double.isInfinite(exp1))
					System.out.println("exp1: " + exp1);
				if(Double.isInfinite(exp2))
					System.out.println("exp2: " + exp2);
				System.out.println("--------");
			}
			//sum += probs[i][j] * (1/Math.pow(variance, numDataPoints)) * Math.exp(exp1) * Math.exp(exp2);
			sum += (1/Math.pow(variance, numDataPoints)) * Math.exp(exp1) * 1/squareRootDets[j] * Math.exp(exp2);
			//}
			newLogLikelihood += Math.log(sum);
		}
		logLikelihood = newLogLikelihood;
		System.out.println("-------------");
		System.out.println(logLikelihood);
		System.out.println("-------------");
	}

	
	/**
	 * Assigns the corresponding class to each gene.
	 */
	private void assignClassToGenes() {
		for(int i=0; i<numGenes; i++) {
			gene2class[i] = findClassOfGene(probs[i]);
		}
	}
	

	/**
	 * Find the class of the gene. That means the class with the maximum
	 * probability.
	 * @param probs
	 * @return
	 */
	private int findClassOfGene(double[] probs) {
		double max = Double.MIN_VALUE;
		int pos = 0;
		for(int i=0; i<probs.length; i++) {
			if(probs[i] > max) {
				max = probs[i];
				pos = i;
			}
		}
		return pos;
	}

	/**
	 * Get some data to test the TimeFit algorithm.
	 */
	private static RealMatrix sampleTestData(int numGenes, int numExperiments, int numClasses) {
		double[][] testData = new double[numExperiments][numGenes];
		double[][] means = new double[numClasses][numExperiments];
		double[][] sds = new double[numClasses][numExperiments];

		// Generate random means and standard deviations. The means are between the
		// given min and max
		Random r = new Random();
		double minMean = -4;
		double maxMean = 4;
		double minSD = .5;
		double maxSD = 1.5;
		for(int i=0; i<numClasses; i++) {
			for(int j=0; j<numExperiments; j++) {
				means[i][j] = minMean + r.nextDouble() * (maxMean-minMean);
				sds[i][j] = minSD + r.nextDouble() * (maxSD-minSD);
			}
		}

		// Now sample the test data
		for(int i=0; i<numGenes; i++) {
			// Choose a random class for the gene
			int clas = r.nextInt(numClasses);
			for(int j=0; j<numExperiments; j++){
				testData[j][i] = means[clas][j] + r.nextGaussian() * sds[clas][j];
			}
		}

		return new Array2DRowRealMatrix(testData);
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {				
		// Test for a time series with x experiments from timepoint 0 to 24
		TimeFit tf = new TimeFit();
		// Generate the test data of 30000 genes, 24 experiments and 5 classes (different sd and means).
		tf.numClasses = 5;
		tf.numGenes = 30000;
		tf.numDataPoints = 24;
		tf.yMatrix = sampleTestData(tf.numGenes, tf.numDataPoints, tf.numClasses); // genes as rows
		System.out.println("yMatrix Rows: " + tf.yMatrix.getRowDimension() + "\tCols: " + tf.yMatrix.getColumnDimension());
		//System.exit(0);
		// Set the time points of the test
		tf.x = new double[tf.numDataPoints];
		for(int i=0; i<tf.numDataPoints; i++) {
			tf.x[i] = i+1; // start with time point 1
		}

		// Test the placing of the control points
		tf.chooseControlPoints(tf.x);

		// Test the placing of the knots
		tf.placeKnots();
		// testing
		for(int i=0; i<tf.knots.length; i++) {
			System.out.println("Knot " + i + ": " + tf.knots[i]);
		}

		// Compute the values of the basis B-splines. The result is a matrix
		// S, where S_[ij] = b_{j,4}(t_i)
		tf.computeS();

		// 1. Initialize the class center for each class as described in the paper.
		// The center of a class is the mean value of the spline coefficients of genes in the class.
		tf.initializeClassCenter();
		// testing
		System.out.println("Initial class centers: " + tf.mu);

		// Initialize mapping from a class to the set of genes in the class.
		tf.initializeGenes2ClassMapping();

		// Compute the covariance matrix for each class
		tf.initializeCovMatrices();

		// Initialize the inverse covariance matrices.
		tf.computeInverseCovMatrices();	

		// Sample the gene specific variation coefficients. Each gene has q (number of control points)
		// variation coefficients, which are sampled with the class covariance matrix.
		tf.computeGamma();

		// Initialize the gene variances and sample the noise vector for each gene.
		tf.computeGeneVariances();

		// Sample the noise vector for each gene
		tf.sampleNoiseVectors();

		// Now find the best parameters and class assignment with an EM-algorithm
		tf.computeClassProbs();

		
		double threshold = 0.005;
		tf.logLikelihood = 2;
		double oldLogLikelihood = 1;


		//while(Math.abs(tf.logLikelihood - oldLogLikelihood) > threshold) {
		int count = 0;
		while(tf.logLikelihood / oldLogLikelihood < 1 - threshold || count < 5) {
			// increase counter
			count++;
			//System.out.println("Diff: " + Math.abs(tf.logLikelihood - oldLogLikelihood));
			oldLogLikelihood = tf.logLikelihood;
			/*
			 * E-Step:
			 * 
			 * For all genes i and classes j, compute P(j|i). This is the probability, that gene i
			 * belongs to class j.
			 */ 
			tf.computeProbabilities();

			/*
			 * M-Step
			 * 
			 * For all genes i and classes j, find the MAP estimate of gamma_ij
			 */
			tf.findMAPEstimate();

			// Maximize the covariance matrices Γ, the variance and the class centers mu
			tf.maximizeVariance();

			// Compute the new class centers mu. To do this, we have to compute two matrices and
			// multiplicate them
			tf.maximizeMu();

			// Compute the new class covariance matrices.
			tf.maximizeCovMatrices();

			// Update the class probabilities
			tf.updateClassProbs();

			// Compute the new log likelihood.
			tf.computeLogLikelihood();

			System.out.println("Fraction: " + (tf.logLikelihood / oldLogLikelihood) + " Threshold: " + (1 - threshold));
		}
	}
}