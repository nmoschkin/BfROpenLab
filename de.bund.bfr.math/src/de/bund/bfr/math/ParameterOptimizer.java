/*******************************************************************************
 * Copyright (c) 2014 Federal Institute for Risk Assessment (BfR), Germany 
 * 
 * Developers and contributors are 
 * Christian Thoens (BfR)
 * Armin A. Weiser (BfR)
 * Matthias Filter (BfR)
 * Alexander Falenski (BfR)
 * Annemarie Kaesbohrer (BfR)
 * Bernd Appel (BfR)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.bund.bfr.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.analysis.MultivariateMatrixFunction;
import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointVectorValuePair;
import org.apache.commons.math3.optim.nonlinear.vector.ModelFunction;
import org.apache.commons.math3.optim.nonlinear.vector.ModelFunctionJacobian;
import org.apache.commons.math3.optim.nonlinear.vector.Target;
import org.apache.commons.math3.optim.nonlinear.vector.Weight;
import org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer;
import org.nfunk.jep.ParseException;

public class ParameterOptimizer {

	private static final double EPSILON = 0.00001;
	private static final int MAX_EVAL = 10000;
	private static final double COV_THRESHOLD = 1e-14;

	private MultivariateVectorFunction optimizerFunction;
	private MultivariateMatrixFunction optimizerFunctionJacobian;

	private List<String> parameters;
	private Map<String, Double> minStartValues;
	private Map<String, Double> maxStartValues;
	private List<Double> targetValues;

	private LevenbergMarquardtOptimizer optimizer;
	private PointVectorValuePair optimizerValues;

	private boolean successful;
	private Map<String, Double> parameterValues;
	private Map<String, Double> parameterStandardErrors;
	private Map<String, Double> parameterTValues;
	private Map<String, Double> parameterPValues;
	private Map<String, Map<String, Double>> covariances;
	private Double sse;
	private Double mse;
	private Double rmse;
	private Double r2;
	private Double aic;

	public ParameterOptimizer(String formula, List<String> parameters,
			Map<String, Double> minStartValues,
			Map<String, Double> maxStartValues,
			Map<String, Double> minParameterValues,
			Map<String, Double> maxParameterValues, List<Double> targetValues,
			Map<String, List<Double>> variableValues, boolean enforceLimits)
			throws ParseException {
		this.parameters = parameters;
		this.minStartValues = minStartValues;
		this.maxStartValues = maxStartValues;
		this.targetValues = targetValues;

		if (enforceLimits) {
			for (String param : parameters) {
				Double min = minParameterValues.get(param);
				Double max = maxParameterValues.get(param);

				if (min != null) {
					formula += "+1000000*(" + param + "<" + min + ")";
				}

				if (max != null) {
					formula += "+1000000*(" + param + ">" + max + ")";
				}
			}
		}

		optimizerFunction = new VectorFunction(formula, parameters,
				variableValues);
		optimizerFunctionJacobian = new VectorFunctionJacobian(formula,
				parameters, variableValues);
		successful = false;
		resetResults();
	}

	public ParameterOptimizer(String formula, List<String> parameters,
			Map<String, Double> minStartValues,
			Map<String, Double> maxStartValues, List<Double> targetValues,
			String valueVariable, String timeVariable,
			Map<String, List<Double>> variableValues) throws ParseException {
		this.parameters = parameters;
		this.minStartValues = minStartValues;
		this.maxStartValues = maxStartValues;
		this.targetValues = targetValues;

		optimizerFunction = new VectorDiffFunction(formula, parameters,
				valueVariable, timeVariable, variableValues,
				targetValues.get(0));
		optimizerFunctionJacobian = new VectorDiffFunctionJacobian(formula,
				parameters, valueVariable, timeVariable, variableValues,
				targetValues.get(0));
		successful = false;
		resetResults();
	}

	public void optimize(int nParameterSpace, int nLevenberg,
			boolean stopWhenSuccessful) throws ParseException {
		List<Double> paramMin = new ArrayList<Double>();
		List<Integer> paramStepCount = new ArrayList<Integer>();
		List<Double> paramStepSize = new ArrayList<Double>();
		int paramsWithRange = 0;
		int maxStepCount = 10;

		for (String param : parameters) {
			Double min = minStartValues.get(param);
			Double max = maxStartValues.get(param);

			if (min != null && max != null) {
				paramsWithRange++;
			}
		}

		if (paramsWithRange != 0) {
			maxStepCount = (int) Math.pow(nParameterSpace,
					1.0 / paramsWithRange);
			maxStepCount = Math.max(maxStepCount, 2);
			maxStepCount = Math.min(maxStepCount, 10);
		}

		for (String param : parameters) {
			Double min = minStartValues.get(param);
			Double max = maxStartValues.get(param);

			if (min != null && max != null) {
				paramMin.add(min);
				paramStepCount.add(maxStepCount);

				if (max > min) {
					paramStepSize.add((max - min) / (maxStepCount - 1));
				} else {
					paramStepSize.add(1.0);
				}
			} else if (min != null) {
				if (min != 0.0) {
					paramMin.add(min);
				} else {
					paramMin.add(EPSILON);
				}

				paramStepCount.add(1);
				paramStepSize.add(1.0);
			} else if (max != null) {
				if (max != 0.0) {
					paramMin.add(max);
				} else {
					paramMin.add(-EPSILON);
				}

				paramStepCount.add(1);
				paramStepSize.add(1.0);
			} else {
				paramMin.add(EPSILON);
				paramStepCount.add(1);
				paramStepSize.add(1.0);
			}
		}

		List<double[]> bestValues = new ArrayList<double[]>();
		List<Double> bestError = new ArrayList<Double>();

		for (int i = 0; i < nLevenberg; i++) {
			double[] v = new double[parameters.size()];

			Arrays.fill(v, i + 1.0);
			bestValues.add(v);
			bestError.add(Double.POSITIVE_INFINITY);
		}

		List<Integer> paramStepIndex = new ArrayList<Integer>(
				Collections.nCopies(parameters.size(), 0));
		boolean done = false;

		while (!done) {
			double[] values = new double[parameters.size()];

			for (int i = 0; i < parameters.size(); i++) {
				values[i] = paramMin.get(i) + paramStepIndex.get(i)
						* paramStepSize.get(i);
			}

			double[] p = optimizerFunction.value(values);
			double error = 0.0;

			for (int i = 0; i < targetValues.size(); i++) {
				if (Double.isNaN(p[i])) {
					error = Double.POSITIVE_INFINITY;
					break;
				}

				double diff = targetValues.get(i) - p[i];

				error += diff * diff;
			}

			for (int i = nLevenberg; i >= 0; i--) {
				if (i == 0 || !(error < bestError.get(i - 1))) {
					if (i != nLevenberg) {
						bestError.add(i, error);
						bestValues.add(i, values);
						bestError.remove(nLevenberg);
						bestValues.remove(nLevenberg);
					}

					break;
				}
			}

			for (int i = 0;; i++) {
				if (i >= parameters.size()) {
					done = true;
					break;
				}

				paramStepIndex.set(i, paramStepIndex.get(i) + 1);

				if (paramStepIndex.get(i) >= paramStepCount.get(i)) {
					paramStepIndex.set(i, 0);
				} else {
					break;
				}
			}
		}

		successful = false;

		for (double[] startValues : bestValues) {
			try {
				optimize(startValues);

				if (!successful || optimizer.getChiSquare() < sse) {
					useCurrentResults();

					if (sse == 0.0) {
						successful = true;
						break;
					}

					if (r2 != null && r2 != 0.0) {
						successful = true;

						if (stopWhenSuccessful) {
							break;
						}
					}
				}
			} catch (TooManyEvaluationsException e) {
				break;
			} catch (ConvergenceException e) {
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (!successful) {
			resetResults();
		}
	}

	public boolean isSuccessful() {
		return successful;
	}

	public Map<String, Double> getParameterValues() {
		return parameterValues;
	}

	public Map<String, Double> getParameterStandardErrors() {
		return parameterStandardErrors;
	}

	public Map<String, Double> getParameterTValues() {
		return parameterTValues;
	}

	public Map<String, Double> getParameterPValues() {
		return parameterPValues;
	}

	public Map<String, Map<String, Double>> getCovariances() {
		return covariances;
	}

	public Double getSSE() {
		return sse;
	}

	public Double getMSE() {
		return mse;
	}

	public Double getRMSE() {
		return rmse;
	}

	public Double getR2() {
		return r2;
	}

	public Double getAIC() {
		return aic;
	}

	public Integer getDOF() {
		return targetValues.size() - parameters.size();
	}

	private void optimize(double[] startValues) throws Exception {
		double[] weights = new double[targetValues.size()];

		Arrays.fill(weights, 1.0);
		optimizer = new LevenbergMarquardtOptimizer();
		optimizerValues = optimizer.optimize(new ModelFunction(
				optimizerFunction), new ModelFunctionJacobian(
				optimizerFunctionJacobian), new MaxEval(MAX_EVAL), new Target(
				ArrayUtils.toPrimitive(targetValues.toArray(new Double[0]))),
				new Weight(weights), new InitialGuess(startValues));
	}

	private void useCurrentResults() {
		parameterValues.clear();
		sse = optimizer.getChiSquare();
		mse = MathUtilities.getMSE(parameters.size(), targetValues.size(), sse);
		rmse = MathUtilities.getRMSE(parameters.size(), targetValues.size(),
				sse);
		r2 = MathUtilities.getR2(sse, targetValues);
		aic = MathUtilities.getAic(parameters.size(), targetValues.size(), sse);

		for (int i = 0; i < parameters.size(); i++) {
			parameterValues.put(parameters.get(i),
					optimizerValues.getPoint()[i]);
		}

		if (targetValues.size() <= parameters.size()) {
			throw new RuntimeException();
		}

		double[][] covMatrix;

		try {
			covMatrix = optimizer.computeCovariances(
					optimizerValues.getPoint(), COV_THRESHOLD);
		} catch (SingularMatrixException e) {
			return;
		}

		double factor = optimizer.getChiSquare()
				/ (targetValues.size() - parameters.size());

		parameterStandardErrors = new LinkedHashMap<String, Double>();
		parameterTValues = new LinkedHashMap<String, Double>();
		parameterPValues = new LinkedHashMap<String, Double>();
		covariances = new LinkedHashMap<String, Map<String, Double>>();

		for (int i = 0; i < parameters.size(); i++) {
			double error = Math.sqrt(factor * covMatrix[i][i]);

			parameterStandardErrors.put(parameters.get(i), error);

			double tValue = optimizerValues.getPoint()[i] / error;
			int degreesOfFreedom = targetValues.size() - parameters.size();

			parameterTValues.put(parameters.get(i), tValue);
			parameterPValues.put(parameters.get(i),
					MathUtilities.getPValue(tValue, degreesOfFreedom));
		}

		for (int i = 0; i < parameters.size(); i++) {
			Map<String, Double> cov = new LinkedHashMap<String, Double>();

			for (int j = 0; j < parameters.size(); j++) {
				cov.put(parameters.get(j), factor * covMatrix[i][j]);
			}

			covariances.put(parameters.get(i), cov);
		}
	}

	private void resetResults() {
		parameterValues = new LinkedHashMap<String, Double>();
		parameterStandardErrors = new LinkedHashMap<String, Double>();
		parameterTValues = new LinkedHashMap<String, Double>();
		parameterPValues = new LinkedHashMap<String, Double>();
		covariances = new LinkedHashMap<String, Map<String, Double>>();
		sse = null;
		mse = null;
		rmse = null;
		r2 = null;
		aic = null;

		for (String param : parameters) {
			covariances.put(param, new LinkedHashMap<String, Double>());
		}
	}

}
