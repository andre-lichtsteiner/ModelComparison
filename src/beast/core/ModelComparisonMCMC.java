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
public class ModelComparisonMCMC extends Runnable {

    final public Input<Integer> chainLengthInput =
            new Input<>("chainLength", "Length of the MCMC chain i.e. number of samples taken in main loop",
                    Input.Validate.REQUIRED);

    final public Input<State> startStateInput =
            new Input<>("state", "elements of the state space");

    final public Input<List<StateNodeInitialiser>> initialisersInput =
            new Input<>("init", "one or more state node initilisers used for determining " +
                    "the start state of the chain",
                    new ArrayList<>());

    final public Input<Integer> storeEveryInput =
            new Input<>("storeEvery", "store the state to disk every X number of samples so that we can " +
                    "resume computation later on if the process failed half-way.", -1);

    final public Input<Integer> burnInInput =
            new Input<>("preBurnin", "Number of burn in samples taken before entering the main loop", 0);


    final public Input<Integer> numInitializationAttempts =
            new Input<>("numInitializationAttempts", "Number of initialization attempts before failing (default=10)", 10);

    final public Input<Distribution> posteriorInput =
            new Input<>("distribution", "probability distribution to sample over (e.g. a posterior)",
                    Input.Validate.REQUIRED);

    final public Input<List<Operator>> operatorsInput =
            new Input<>("operator", "operator for generating proposals in MCMC state space",
                    new ArrayList<>());//, Input.Validate.REQUIRED);

    final public Input<List<Logger>> loggersInput =
            new Input<>("logger", "loggers for reporting progress of MCMC chain",
                    new ArrayList<>(), Input.Validate.REQUIRED);

    final public Input<Boolean> sampleFromPriorInput = new Input<>("sampleFromPrior", "whether to ignore the likelihood when sampling (default false). " +
            "The distribution with id 'likelihood' in the posterior input will be ignored when this flag is set.", false);

    final public Input<OperatorSchedule> operatorScheduleInput = new Input<>("operatorschedule", "specify operator selection and optimisation schedule", new OperatorSchedule());

    final public Input<String> betaControlModeInput = new Input<>("betaControlMode", "specify the way that beta should be controlled across the MCMC chain. valid options: 'static' (don't change beta); 'oneway' (beta will change from  0 to 1 OR 1 to 0); 'bothways' (beta will change in one direction and then return to the start)", Input.Validate.REQUIRED);

    final public Input<RealParameter> betaParameterInput = new Input<>("betaParameter", "the parameter which will be used in calculating the posterior, switching between models");

    /*** Custom things below for ModelComparison ***/
    private Distribution[] innerPosteriors;
    private double[] oldLogLikelihoods;
    private double[] newLogLikelihoods;
    private String betaControlMode;
    private double betaIncrement;
    //private double betaValue;


    /**
     * Alternative representation of operatorsInput that allows random selection
     * of operators and calculation of statistics.
     */
    protected OperatorSchedule operatorSchedule;

    /**
     * The state that takes care of managing StateNodes,
     * operations on StateNodes and propagates store/restore/requireRecalculation
     * calls to the appropriate BEASTObjects.
     */
    protected State state;

    /**
     * number of samples taken where calculation is checked against full
     * recalculation of the posterior. Note that after every proposal that
     * is checked, there are 2 that are not checked. This allows errors
     * in store/restore to be detected that cannot be found when every single
     * consecutive sample is checked.
     * So, only after 3*NR_OF_DEBUG_SAMPLES samples checking is stopped.
     */
    final protected int NR_OF_DEBUG_SAMPLES = 2000;

    /**
     * Interval for storing state to disk, if negative the state will not be stored periodically *
     * Mirrors m_storeEvery input, or if this input is negative, the State.m_storeEvery input
     */
    protected int storeEvery;

    /**
     * Set this to true to enable detailed MCMC debugging information
     * to be displayed.
     */
    private static final boolean printDebugInfo = false;





    @Override
    public void initAndValidate() {
        Log.info.println("===============================================================================");
        Log.info.println("Citations for this model:");
        Log.info.println(getCitations());
        Log.info.println("===============================================================================");


        operatorSchedule = operatorScheduleInput.get();
        for (final Operator op : operatorsInput.get()) {
            operatorSchedule.addOperator(op);
        }

        if (sampleFromPriorInput.get()) {
            // remove beastObject with id likelihood from posterior, if it is a CompoundDistribution
            if (posteriorInput.get() instanceof CompoundDistribution) {
                final CompoundDistribution posterior = (CompoundDistribution) posteriorInput.get();
                final List<Distribution> distrs = posterior.pDistributions.get();
                final int distrCount = distrs.size();
                for (int i = 0; i < distrCount; i++) {
                    final Distribution distr = distrs.get(i);
                    final String id = distr.getID();
                    if (id != null && id.equals("likelihood")) {
                        distrs.remove(distr);
                        break;
                    }
                }
                if (distrs.size() == distrCount) {
                    throw new RuntimeException("Sample from prior flag is set, but distribution with id 'likelihood' is " +
                            "not an input to posterior.");
                }
            }
            else {
                throw new RuntimeException("Don't know how to sample from prior since posterior is not a compound distribution. " +
                        "Suggestion: set sampleFromPrior flag to false.");
            }
        }

        //Should assume that we always are working in the ModelComparison context

        String betaControlMode = betaControlModeInput.get().toLowerCase();
        if (betaControlMode.equals("static") || betaControlMode.equals("oneway") || betaControlMode.equals("bothways")){
            //betaValue = betaParameterInput.get().getValue(); // Just to set its starting value
        }
        else{

            System.out.println("Invalid option specified for betaControlMode (on the ModelComparisonMCMC object)");
            System.out.println("the value you specified was: \"" + betaControlMode + '"');
            System.out.println("valid options are: 'static' (don't change beta); 'oneway' (beta will change from  0 to 1 OR 1 to 0); 'bothways' (beta will change in one direction and then return to the start)");
            throw new IllegalArgumentException("Invalid option specified for betaControlMode (on the ModelComparisonMCMC object)");
        }


        //if (posteriorInput.get() instanceof ModelComparisonDistribution){
            //System.out.println("Posterior is a ModelComparisonDistribution");
            innerPosteriors = new Distribution[2];
            innerPosteriors[0] = ((ModelComparisonDistribution) posteriorInput.get()).pDistributions.get().get(0);
            innerPosteriors[1] = ((ModelComparisonDistribution) posteriorInput.get()).pDistributions.get().get(1);
            oldLogLikelihoods = new double[2];

            //if(innerPosteriors[0] instanceof CompoundDistribution){
            //   System.out.println("InnerPosterior 0 is a compound dist");
            //}
       // }

        // StateNode initialisation, only required when the state is not read from file
        if (restoreFromFile) {
            final HashSet<StateNode> initialisedStateNodes = new HashSet<>();
            for (final StateNodeInitialiser initialiser : initialisersInput.get()) {
                // make sure that the initialiser does not re-initialises a StateNode
                final List<StateNode> list = new ArrayList<>(1);
                initialiser.getInitialisedStateNodes(list);
                for (final StateNode stateNode : list) {
                    if (initialisedStateNodes.contains(stateNode)) {
                        throw new RuntimeException("Trying to initialise stateNode (id=" + stateNode.getID() + ") more than once. " +
                                "Remove an initialiser from MCMC to fix this.");
                    }
                }
                initialisedStateNodes.addAll(list);
                // do the initialisation
                //initialiser.initStateNodes();
            }
        }

        // State initialisation
        final HashSet<StateNode> operatorStateNodes = new HashSet<>();
        for (final Operator op : operatorsInput.get()) {
            for (final StateNode stateNode : op.listStateNodes()) {
                operatorStateNodes.add(stateNode);
            }
        }
        if (startStateInput.get() != null) {
            this.state = startStateInput.get();
            if (storeEveryInput.get() > 0) {
                this.state.m_storeEvery.setValue(storeEveryInput.get(), this.state);
            }
        } else {
            // create state from scratch by collecting StateNode inputs from Operators
            this.state = new State();
            for (final StateNode stateNode : operatorStateNodes) {
                this.state.stateNodeInput.setValue(stateNode, this.state);
            }
            this.state.m_storeEvery.setValue(storeEveryInput.get(), this.state);
        }

        // grab the interval for storing the state to file
        if (storeEveryInput.get() > 0) {
            storeEvery = storeEveryInput.get();
        } else {
            storeEvery = state.m_storeEvery.get();
        }

        this.state.initialise();
        this.state.setPosterior(posteriorInput.get());

        // sanity check: all operator state nodes should be in the state
        final List<StateNode> stateNodes = this.state.stateNodeInput.get();
        for (final Operator op : operatorsInput.get()) {
            List<StateNode> nodes = op.listStateNodes();
            if (nodes.size() == 0) {
                throw new RuntimeException("Operator " + op.getID() + " has no state nodes in the state. "
                        + "Each operator should operate on at least one estimated state node in the state. "
                        + "Remove the operator or add its statenode(s) to the state and/or set estimate='true'.");
                // otherwise the chain may hang without obvious reason
            }
            for (final StateNode stateNode : op.listStateNodes()) {
                if (!stateNodes.contains(stateNode)) {
                    throw new RuntimeException("Operator " + op.getID() + " has a statenode " + stateNode.getID() + " in its inputs that is missing from the state.");
                }
            }
        }

        // sanity check: at least one operator required to run MCMC
        if (operatorsInput.get().size() == 0) {
            Log.warning.println("Warning: at least one operator required to run the MCMC properly, but none found.");
        }

        // sanity check: all state nodes should be operated on
        for (final StateNode stateNode : stateNodes) {
            if (!operatorStateNodes.contains(stateNode)) {
                Log.warning.println("Warning: state contains a node " + stateNode.getID() + " for which there is no operator.");
            }
        }
    } // init

    public void log(final int sampleNr) {
        for (final Logger log : loggers) {
            log.log(sampleNr);
        }
    } // log

    public void close() {
        for (final Logger log : loggers) {
            log.close();
        }
    } // close

    protected double logAlpha;
    protected boolean debugFlag;
    protected double oldLogLikelihood;
    protected double newLogLikelihood;
    protected int burnIn;
    protected int chainLength;
    protected Distribution posterior;

    protected List<Logger> loggers;

    @Override
    public void run() throws IOException, SAXException, ParserConfigurationException {
        // set up state (again). Other beastObjects may have manipulated the
        // StateNodes, e.g. set up bounds or dimensions
        state.initAndValidate();
        // also, initialise state with the file name to store and set-up whether to resume from file
        state.setStateFileName(stateFileName);
        operatorSchedule.setStateFileName(stateFileName);

        burnIn = burnInInput.get();
        chainLength = chainLengthInput.get();
        int initialisationAttempts = 0;
        state.setEverythingDirty(true);
        posterior = posteriorInput.get();

        if (restoreFromFile) {
            state.restoreFromFile();
            operatorSchedule.restoreFromFile();
            burnIn = 0;
            oldLogLikelihood = state.robustlyCalcPosterior(posterior);
        } else {
            do {
                for (final StateNodeInitialiser initialiser : initialisersInput.get()) {
                    initialiser.initStateNodes();
                }
                oldLogLikelihood = state.robustlyCalcPosterior(posterior);
                initialisationAttempts += 1;
            } while (Double.isInfinite(oldLogLikelihood) && initialisationAttempts < numInitializationAttempts.get());
        }
        final long startTime = System.currentTimeMillis();

        state.storeCalculationNodes();


      //  if (posterior instanceof ModelComparisonDistribution){
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
     //   }

        // do the sampling
        logAlpha = 0;
        debugFlag = Boolean.valueOf(System.getProperty("beast.debug"));
        debugFlag = true;


//        System.err.println("Start state:");
//        System.err.println(state.toString());

        Log.info.println("Start likelihood: " + oldLogLikelihood + " " + (initialisationAttempts > 1 ? "after " + initialisationAttempts + " initialisation attempts" : ""));
        if (Double.isInfinite(oldLogLikelihood) || Double.isNaN(oldLogLikelihood)) {
            reportLogLikelihoods(posterior, "");
            throw new RuntimeException("Could not find a proper state to initialise. Perhaps try another seed.");
        }

        loggers = loggersInput.get();

        // put the loggers logging to stdout at the bottom of the logger list so that screen output is tidier.
        Collections.sort(loggers, (o1, o2) -> {
            if (o1.isLoggingToStdout()) {
                return o2.isLoggingToStdout() ? 0 : 1;
            } else {
                return o2.isLoggingToStdout() ? -1 : 0;
            }
        });
        // warn if none of the loggers is to stdout, so no feedback is given on screen
        boolean hasStdOutLogger = false;
        boolean hasScreenLog = false;
        for (Logger l : loggers) {
            if (l.isLoggingToStdout()) {
                hasStdOutLogger = true;
            }
            if (l.getID() != null && l.getID().equals("screenlog")) {
                hasScreenLog = true;
            }
        }
        if (!hasStdOutLogger) {
            Log.warning.println("WARNING: If nothing seems to be happening on screen this is because none of the loggers give feedback to screen.");
            if (hasScreenLog) {
                Log.warning.println("WARNING: This happens when a filename  is specified for the 'screenlog' logger.");
                Log.warning.println("WARNING: To get feedback to screen, leave the filename for screenlog blank.");
                Log.warning.println("WARNING: Otherwise, the screenlog is saved into the specified file.");
            }
        }

        // initialises log so that log file headers are written, etc.
        for (final Logger log : loggers) {
            log.init();
        }

        doLoop();

        Log.info.println();
        operatorSchedule.showOperatorRates(System.out);

        Log.info.println();
        final long endTime = System.currentTimeMillis();
        Log.info.println("Total calculation time: " + (endTime - startTime) / 1000.0 + " seconds");
        close();

        Log.warning.println("End likelihood: " + oldLogLikelihood);
//        System.err.println(state);
        state.storeToFile(chainLength);
        operatorSchedule.storeToFile();
        //Randomizer.storeToFile(stateFileName);
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
            return false;
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
    protected void doLoop() throws IOException {
        int corrections = 0;
        final boolean isStochastic = posterior.isStochastic();

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

    private boolean isTooDifferent(double logLikelihood, double originalLogP) {
        //return Math.abs((logLikelihood - originalLogP)/originalLogP) > 1e-6;
        return Math.abs(logLikelihood - originalLogP) > 1e-6;
    }


    /*
     * report posterior and subcomponents recursively, for debugging
     * incorrectly recalculated posteriors *
     */
    protected void reportLogLikelihoods(final Distribution distr, final String tabString) {
        final double full =  distr.logP, last = distr.storedLogP;
        final String changed = full == last ? "" : "  **";
        Log.err.println(tabString + "P(" + distr.getID() + ") = " + full + " (was " + last + ")" + changed);
        if (distr instanceof CompoundDistribution) {
            for (final Distribution distr2 : ((CompoundDistribution) distr).pDistributions.get()) {
                reportLogLikelihoods(distr2, tabString + "\t");
            }
        }
    }

    protected void callUserFunction(final int sample) {
    }


    /**
     * Calculate posterior by setting all StateNodes and CalculationNodes dirty.
     * Clean everything afterwards.
     */
    public double robustlyCalcPosterior(final Distribution posterior) {
        return state.robustlyCalcPosterior(posterior);
    }


    /**
     * Calculate posterior by setting all StateNodes and CalculationNodes dirty.
     * Clean everything afterwards.
     */
    public double robustlyCalcNonStochasticPosterior(final Distribution posterior) {
        return state.robustlyCalcNonStochasticPosterior(posterior);
    }
} // class MCMC

