/*
* File MCMC.java
*
* Copyright (C) 2010 Remco Bouckaert remco@cs.auckland.ac.nz
*
* This file is part of BEAST2.
* See the NOTICE file distributed with this work for additional
* information regarding copyright ownership and licensing.
*
* BEAST is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
*  BEAST is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with BEAST; if not, write to the
* Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
* Boston, MA  02110-1301  USA
*/
package beast.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import beast.core.parameter.RealParameter;
import beast.math.distributions.ModelComparisonDistribution;
import beast.util.InputType;
import org.antlr.v4.runtime.Parser;
import org.xml.sax.SAXException;

import beast.core.util.CompoundDistribution;
import beast.core.util.Evaluator;
import beast.core.util.Log;
import beast.util.Randomizer;

@Description("MCMC chain. This is the main element that controls which posterior " +
        "to calculate, how long to run the chain and all other properties, " +
        "which operators to apply on the state space and where to log results.")
@Citation(value=
        "Bouckaert RR, Heled J, Kuehnert D, Vaughan TG, Wu C-H, Xie D, Suchard MA,\n" +
                "  Rambaut A, Drummond AJ (2014) BEAST 2: A software platform for Bayesian\n" +
                "  evolutionary analysis. PLoS Computational Biology 10(4): e1003537"
        , year = 2014, firstAuthorSurname = "bouckaert",
        DOI="10.1371/journal.pcbi.1003537")
public class ModelComparisonMCMC extends MCMC {

    final public Input<String> betaControlModeInput = new Input<>("betaControlMode", "specify the way that beta should be controlled across the MCMC chain. valid options: 'static' (don't change beta); 'oneway' (beta will change from  0 to 1 OR 1 to 0); 'bothways' (beta will change in one direction and then return to the start)", Input.Validate.REQUIRED);

    final public Input<RealParameter> betaParameterInput = new Input<>("betaParameter", "the parameter which will be used in calculating the posterior, switching between models");

    /*** Custom things below for ModelComparison ***/
    private Distribution[] innerPosteriors;
    private double[] oldLogLikelihoods;
    private double[] newLogLikelihoods;
    private String betaControlMode;
    private double betaIncrement;



   //Because it is private, need to have this unless decide to not use it:
    private static final boolean printDebugInfo = false;

    @Override
    public void initAndValidate() {
        super.initAndValidate();

        //Should assume that we always are working in the ModelComparison context

        betaControlMode = betaControlModeInput.get().toLowerCase();
        if (betaControlMode.equals("static") || betaControlMode.equals("oneway") || betaControlMode.equals("bothways")){
            //betaValue = betaParameterInput.get().getValue(); // Just to set its starting value
        }
        else{

            System.out.println("Invalid option specified for betaControlMode (on the ModelComparisonMCMC object)");
            System.out.println("the value you specified was: \"" + betaControlMode + '"');
            System.out.println("valid options are: 'static' (don't change beta); 'oneway' (beta will change from  0 to 1 OR 1 to 0); 'bothways' (beta will change in one direction and then return to the start)");
            throw new IllegalArgumentException("Invalid option specified for betaControlMode (on the ModelComparisonMCMC object)");
        }


            innerPosteriors = new Distribution[2];
            innerPosteriors[0] = ((ModelComparisonDistribution) posteriorInput.get()).pDistributions.get().get(0);
            innerPosteriors[1] = ((ModelComparisonDistribution) posteriorInput.get()).pDistributions.get().get(1);
            oldLogLikelihoods = new double[2];



    } // init

    @Override
    public void run() throws IOException, SAXException, ParserConfigurationException {
        super.run();

        oldLogLikelihoods[0] = innerPosteriors[0].calculateLogP();
        oldLogLikelihoods[1] = innerPosteriors[1].calculateLogP();
        ((ModelComparisonDistribution) posterior).cacheInnerLogPValues(oldLogLikelihoods);

        if(betaControlMode != "static"){
            // if (((ModelComparisonDistribution) posterior).betaControlAutomatically) {
            //double intervalSide0 = 1.0 - ((ModelComparisonDistribution) posterior).betaValue;
            double intervalSide0 = 1.0 - betaParameterInput.get().getValue();
            double intervalSide1 = 1.0 - intervalSide0;

            double betaIntervalSize = Math.abs(intervalSide0 - intervalSide1);
            double incrementSignFactor = 1.0;
            if(intervalSide0 < intervalSide1){
                //Use negative increments
                incrementSignFactor = -1.0;
            }
            //((ModelComparisonDistribution) posterior).betaIncrement = (betaIntervalSize / chainLength) * incrementSignFactor; //TODO this is currently very hacky
            betaIncrement = (betaIntervalSize / chainLength) * incrementSignFactor; //TODO this is currently very hacky


            //System.out.println("Chain length: " + chainLength);
            //System.out.println("Have set betaIncrement to be: " + (1.0 / chainLength));
            //System.out.println("Checking its value: " + ((ModelComparisonDistribution) posterior).betaIncrement);
        }

    } // run;


    private boolean incrementBetaIfRequired(){
        if(betaControlMode == "oneway") {
            //Increment beta sliiiiightly
            //double betaIncrement = ((ModelComparisonDistribution) posterior).betaIncrement;

            //betaValue = betaValue + betaIncrement;
            betaParameterInput.get().setValue(betaParameterInput.get().getValue() + betaIncrement);
            return true;

        }
        else if (betaControlMode == "bothways"){
            //Do same as for one way, but also once beta gets to the other extreme (based on chainLength), need to invert betaIncrement for the next step
            //at the initialisation time the betaIncrement should take into account the size of



            return false; //Should return true once it is set up correctly.
        }
        else { //ie. if "static"
            return false;
        }

        /*
        if (((ModelComparisonDistribution) posterior).betaControlAutomatically){
            //Increment beta sliiiiightly
            double betaIncrement = ((ModelComparisonDistribution) posterior).betaIncrement;

            ((ModelComparisonDistribution) posterior).betaValue = ((ModelComparisonDistribution) posterior).betaValue + betaIncrement;
            return true;
        }
        else{
            return false;
        }
        */
    }

    private double recalculateOldLogLikelihoodWithNewBeta(){
        //double[] inner_LogLikelihoods = new double[2];


        oldLogLikelihoods = new double[2]; //old here means before the operator does something

        oldLogLikelihoods[0] = innerPosteriors[0].calculateLogP();
        oldLogLikelihoods[1] = innerPosteriors[1].calculateLogP();
        ((ModelComparisonDistribution) posterior).cacheInnerLogPValues(oldLogLikelihoods);

        return ((ModelComparisonDistribution) posterior).calculateLogPFromInnerLogPValues(oldLogLikelihoods);


        //inner_LogLikelihoods[0] = innerPosteriors[0].calculateLogP();
        //inner_LogLikelihoods[1] = innerPosteriors[1].calculateLogP();
        //Not sure if the above is going to cause some kind of issue elsewhere?
        //System.out.println("oldLogLikelihoods[0] = " + oldLogLikelihoods[0]);
        //System.out.println("oldLogLikelihoods[1] = " + oldLogLikelihoods[1]);
        //System.out.println("newLogLikelihoods[0] = " + newLogLikelihoods[0]);
        //System.out.println("newLogLikelihoods[1] = " + newLogLikelihoods[1]);

    }



    /**
     * main MCMC loop
     * @throws IOException *
     */

    @Override
    protected void doLoop() throws IOException {

        //Note that a solid chunk of this is copied straight from the MCMC.java code. Perhaps there is a way to streamline/not repeat it - unclear how that would work though

        int corrections = 0;
        final boolean isStochastic = super.posterior.isStochastic();

        if (burnIn > 0) {
            Log.warning.println("Please wait while BEAST takes " + burnIn + " pre-burnin samples");
        }
        for (int sampleNr = -burnIn; sampleNr <= chainLength; sampleNr++) {


            final int currentState = sampleNr;

            if(posterior instanceof ModelComparisonDistribution){
                if(incrementBetaIfRequired()){ //Returns true if beta was incremented
                    oldLogLikelihood = recalculateOldLogLikelihoodWithNewBeta(); // oldLogLikelihoods are updated also
                }
            }
            state.store(currentState);
//            if (m_nStoreEvery > 0 && sample % m_nStoreEvery == 0 && sample > 0) {
//                state.storeToFile(sample);
//            	operatorSchedule.storeToFile();
//            }

            final Operator operator = operatorSchedule.selectOperator();

            if (printDebugInfo) System.err.print("\n" + sampleNr + " " + operator.getName()+ ":");

            final Distribution evaluatorDistribution = operator.getEvaluatorDistribution();
            Evaluator evaluator = null;

            if (evaluatorDistribution != null) {
                evaluator = new Evaluator() {
                    @Override
                    public double evaluate() {
                        double logP = 0.0;

                        state.storeCalculationNodes();
                        state.checkCalculationNodesDirtiness();

                        try {
                            logP = evaluatorDistribution.calculateLogP();
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.exit(1);
                        }

                        state.restore();
                        state.store(currentState);

                        return logP;
                    }
                };
            }
            final double logHastingsRatio = operator.proposal(evaluator);

            if (logHastingsRatio != Double.NEGATIVE_INFINITY) {

                if (operator.requiresStateInitialisation()) {
                    state.storeCalculationNodes();
                    state.checkCalculationNodesDirtiness();
                }

                // Rejig this for when posterior is a ModelComparisonDistribution
                newLogLikelihoods = new double[2];
                if (posterior instanceof ModelComparisonDistribution){
                    newLogLikelihoods[0] = innerPosteriors[0].calculateLogP();
                    newLogLikelihoods[1] = innerPosteriors[1].calculateLogP();
                    //Not sure if the above is going to cause some kind of issue elsewhere?
                    //System.out.println("oldLogLikelihoods[0] = " + oldLogLikelihoods[0]);
                    //System.out.println("oldLogLikelihoods[1] = " + oldLogLikelihoods[1]);
                    //System.out.println("newLogLikelihoods[0] = " + newLogLikelihoods[0]);
                    //System.out.println("newLogLikelihoods[1] = " + newLogLikelihoods[1]);

                    //Disabling the following for now - just do the usual way of deciding about an operator acceptance

                    //The following evaluates the operator based on howit affects one of the inner posteriors, rather than how it affects the whole posterior, as this helps when beta is at one of the extremes
                    /*
                    //Should perhaps be split based on if the change ONLY affected one of the models, otherwise base it off overall effect
                    if (Math.abs(newLogLikelihoods[0] - oldLogLikelihoods[0]) > 0.0000001 && Math.abs(newLogLikelihoods[1] - oldLogLikelihoods[1]) < 0.0000001){
                        logAlpha = newLogLikelihoods[0] - oldLogLikelihoods[0] + logHastingsRatio;
                        //System.out.println("-- Affected (substantially) model 0 only");
                    }
                    else if (Math.abs(newLogLikelihoods[1] - oldLogLikelihoods[1]) > 0.0000001 && Math.abs(newLogLikelihoods[0] - oldLogLikelihoods[0]) < 0.0000001){
                        logAlpha = newLogLikelihoods[1] - oldLogLikelihoods[1] + logHastingsRatio;
                       // System.out.println("-- Affected (substantially) model 1 only");
                    }
                    else{

                        //System.out.println("------------------------- Must have affected both/neither model ------------------------");
                        logAlpha = ((ModelComparisonDistribution) posterior).calculateLogPFromInnerLogPValues(newLogLikelihoods) - oldLogLikelihood + logHastingsRatio;
                    }
                    */

                    //Below line is if not doing anything fancy like the above, just the usual operator acceptance functionality
                    logAlpha = ((ModelComparisonDistribution) posterior).calculateLogPFromInnerLogPValues(newLogLikelihoods) - oldLogLikelihood + logHastingsRatio;

                    //logAlpha = Math.max(newLogLikelihoods[0] - oldLogLikelihoods[0] + logHastingsRatio, newLogLikelihoods[1] - oldLogLikelihoods[1] + logHastingsRatio);
                    // System.out.println("logHastingsRatio = " + logHastingsRatio);
                    //System.out.println("logAlpha = " + logAlpha);
                    //This may or may not be correct
                }
                else{ //Original stuff
                    newLogLikelihood = posterior.calculateLogP();
                    logAlpha = newLogLikelihood - oldLogLikelihood + logHastingsRatio; //CHECK HASTINGS
                    if (printDebugInfo) System.err.print(logAlpha + " " + newLogLikelihood + " " + oldLogLikelihood);
                }


                //Before acceptance, update oldLogLikelihood based on the new beta value if need be
                //if (posterior instanceof ModelComparisonDistribution){
                //    oldLogLikelihoods = newLogLikelihoods;
                //    //newLogLikelihood = posterior.calculateLogP(); // Can make this more efficient by not doing a combination calcualtion rather than re doing the whole calculation again //Need to update full posterior LogP also or otherwise confusion will follow
                //    newLogLikelihood = ((ModelComparisonDistribution) posterior).calculateLogPFromInnerLogPValues(newLogLikelihoods); //This should be more efficient
                //    oldLogLikelihood = newLogLikelihood;
                //}


                if (logAlpha >= 0 || Randomizer.nextDouble() < Math.exp(logAlpha)) {
                    // accept
                    if (posterior instanceof ModelComparisonDistribution){
                        oldLogLikelihoods = newLogLikelihoods;
                        //newLogLikelihood = posterior.calculateLogP(); // Can make this more efficient by not doing a combination calcualtion rather than re doing the whole calculation again //Need to update full posterior LogP also or otherwise confusion will follow
                        newLogLikelihood = ((ModelComparisonDistribution) posterior).calculateLogPFromInnerLogPValues(newLogLikelihoods); //This should be more efficient
                        oldLogLikelihood = newLogLikelihood;
                        ((ModelComparisonDistribution) posterior).cacheInnerLogPValues(oldLogLikelihoods); //Update after the operator has now done its thing
                    }
                    else {
                        oldLogLikelihood = newLogLikelihood;
                    }

                    state.acceptCalculationNodes();

                    if (sampleNr >= 0) {
                        operator.accept();
                    }
                    if (printDebugInfo) System.err.print(" accept");
                } else {
                    // reject
                    if (sampleNr >= 0) {
                        if(posterior instanceof ModelComparisonDistribution){
                            if (newLogLikelihoods[0] == Double.NEGATIVE_INFINITY || newLogLikelihoods[1] == Double.NEGATIVE_INFINITY){
                                operator.reject(-1);
                            }
                            else{
                                operator.reject(0);
                            }
                        }
                        else {
                            operator.reject(newLogLikelihood == Double.NEGATIVE_INFINITY ? -1 : 0);
                        }
                    }

                    state.restore();
                    state.restoreCalculationNodes();
                    if (printDebugInfo) System.err.print(" reject");
                }
                state.setEverythingDirty(false);
            } else {
                // operation failed
                if (sampleNr >= 0) {
                    operator.reject(-2);
                }
                state.restore();
                if (!operator.requiresStateInitialisation()) {
                    state.setEverythingDirty(false);
                    state.restoreCalculationNodes();
                }
                if (printDebugInfo) System.err.print(" direct reject");
            }
            log(sampleNr);

            if (debugFlag && sampleNr % 3 == 0 || sampleNr % 10000 == 0) {
                // check that the posterior is correctly calculated at every third
                // sample, as long as we are in debug mode
                final double originalLogP = isStochastic ? posterior.getNonStochasticLogP() : oldLogLikelihood;
                final double logLikelihood = isStochastic ? state.robustlyCalcNonStochasticPosterior(posterior) : state.robustlyCalcPosterior(posterior);
                if (isTooDifferent(logLikelihood, originalLogP)) {
                    reportLogLikelihoods(posterior, "");
                    Log.err.println("At sample " + sampleNr + "\nLikelihood incorrectly calculated: " + originalLogP + " != " + logLikelihood
                            + "(" + (originalLogP - logLikelihood) + ")"
                            + " Operator: " + operator.getClass().getName());
                }
                if (sampleNr > NR_OF_DEBUG_SAMPLES * 3) {
                    // switch off debug mode once a sufficient large sample is checked
                    debugFlag = false;
                    if (isTooDifferent(logLikelihood, originalLogP)) {
                        // incorrect calculation outside debug period.
                        // This happens infrequently enough that it should repair itself after a robust posterior calculation
                        corrections++;
                        if (corrections > 100) {
                            // after 100 repairs, there must be something seriously wrong with the implementation
                            Log.err.println("Too many corrections. There is something seriously wrong that cannot be corrected");
                            state.storeToFile(sampleNr);
                            operatorSchedule.storeToFile();
                            System.exit(1);
                        }
                        oldLogLikelihood = state.robustlyCalcPosterior(posterior);;
                    }
                } else {
                    if (isTooDifferent(logLikelihood, originalLogP)) {
                        // halt due to incorrect posterior during intial debug period
                        state.storeToFile(sampleNr);
                        operatorSchedule.storeToFile();
                        System.exit(1);
                    }
                }
            } else {
                if (sampleNr >= 0) {
                    operator.optimize(logAlpha);
                }
            }
            callUserFunction(sampleNr);

            // make sure we always save just before exiting
            if (storeEvery > 0 && (sampleNr + 1) % storeEvery == 0 || sampleNr == chainLength) {
                /*final double logLikelihood = */
                state.robustlyCalcNonStochasticPosterior(posterior);
                state.storeToFile(sampleNr);
                operatorSchedule.storeToFile();
            }

            /* Previously had this down here
            //Update the value of beta, if asked to
            if(posterior instanceof ModelComparisonDistribution){
                if (((ModelComparisonDistribution) posterior).betaControlAutomatically){
                    //Increment beta sliiiiightly
                    double betaIncrement = ((ModelComparisonDistribution) posterior).betaIncrement;
                    //System.out.println(betaIncrement);

                    ((ModelComparisonDistribution) posterior).betaValue = ((ModelComparisonDistribution) posterior).betaValue + betaIncrement;
                    //System.out.println(((ModelComparisonDistribution) posterior).betaValue);
                }
            }
            */

        }
        if (corrections > 0) {
            Log.err.println("\n\nNB: " + corrections + " posterior calculation corrections were required. This analysis may not be valid!\n\n");
        }
    }


    //Passing through all methods (because otherwise there will be an error, would need to cast this instanc of ModelComparisonMCMC to an MCMC object to cal these otherwise?

    public void log(final int sampleNr) { super.log(sampleNr); } // log

    public void close() { super.close(); } // close

    public double robustlyCalcNonStochasticPosterior(final Distribution posterior) { return super.robustlyCalcNonStochasticPosterior(posterior); }

    public double robustlyCalcPosterior(final Distribution posterior) { return super.robustlyCalcPosterior(posterior); }

    protected void callUserFunction(final int sample) {super.callUserFunction(sample);}

    protected void reportLogLikelihoods(final Distribution distr, final String tabString) {super.reportLogLikelihoods(distr, tabString);}

    private boolean isTooDifferent(double logLikelihood, double originalLogP) { //Can't do super.thismethod because it is private
        //return Math.abs((logLikelihood - originalLogP)/originalLogP) > 1e-6;
        return Math.abs(logLikelihood - originalLogP) > 1e-6;
    }

} // class MCMC

