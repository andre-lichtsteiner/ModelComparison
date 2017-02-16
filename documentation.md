---
layout: page
title: Documentation
permalink: /documentation/

#Have used (??) where I am not sure of something
---
## About the package

This package is primarily based on the quasistatic version of the "model switch integration method" laid out in a paper by *{{site.lartillot_paper_authors}}* titled [{{site.lartillot_paper_title}}]({{site.lartillot_paper_link}})

The modification we have made to their method is changing the value of beta at *every* step of the MCMC chain rather than at set intervals. Beta is therefore changed **very** slowly, so as to not cause issues with equilibration.

<h4>Summary of the method</h4>
The main idea of this method is to progressively change the target density from being the posterior of one model to the posterior of another model, which allows estimating the Bayes factor.

This is accomplished by setting the posterior density to the product of the two model posteriors, with one raised to the power of the parameter **beta** and the other raised to **1 - beta**.

Regularly across the chain, a value called **U** in the {{site.lartillot_paper_authors}} paper is calculated which is later used in estimating the Bayes factor.

Please read the above paper for more understanding of this method.

## Installation

Please refer to the [instructions for installation]({{site.baseurl}}/installation/)

## Basics of Usage

This package can be used to calculate the Bayes factor of one model over another.
It can be used in certain cases where path sampling fails, particularly where sampling from the prior is simply impractical.

One use of this is for structured trees (eg. using [MultiTypeTree](https://tgvaughan.github.io/MultiTypeTree/)) which become challenging when there are more than ust a few demes.

It is also worth checking out the [tutorials](/tutorials/).

**General overview of how this package can be used:**

1. Install the ModelComparison package (?? More details required - maybe even a separate page?)
2. Setup a BEAST XML file which specifies your analysis (see tutorials and below documentation)
3. Equilibrate the run (run an initial chain until equilibrated) (?? More detail)
4. Modify one value in the XML file (telling ModelComparison to progressively switch between your two models)
5. Resume the run using the changed XML file 
6. Run the command line tool (built-in) to estimate the Bayes factor using the log file

## Overview of the package

This package uses:

- a customised version of BEAST's MCMC chain which allows the beta value to be changed incrementally and evenly throughout the course of the run.
- a customised distribution which allows you to switch between two posteriors depending on the value of beta
- a logger which records the beta and U values as often as you wish
- a tool which uses the recorded beta and U values to estimate the Bayes factor

You must specify the first three of these to be used within your BEAST XML file or the run will not result in a comparison of the two models.

## BEAST Objects
<div class="beast_object">
<h3><span class='class_path'>beast.math.distributions.</span><span class="class_name">ModelComparisonDistribution</span></h3>

This BEAST object is a Distribution, and it does the work of calculating the posterior using the beta value and the two provided Distribution inputs.

<h4>Options/Inputs</h4>
<span class="member_name_precursor">name:</span><span class="member_name">betaParameter</span>
<ul>
<li>This is a RealParameter (ie. a decimal number) which is used by ModelComparisonDistribution in calculating the posterior. </li>
<li>Note:</li>
<li>This should be set to either 0 or 1 in your XML</li>
<li>Although you can choose other values if you wish, ModelComparison won't stop you. If you wanted to explore just a subset of the 0 to 1 space, you can do this by setting the value of the betaValue to something like 0.3, and then it will go from 0.3 to 0.7, or from 0.1 to 0.1 (is this useful to someone?) (?? Also does it still work this way?)</li>
</ul>


<span class="member_name_precursor">name:</span><span class="member_name">distribution</span>
<ul>
<li>You must provide exactly two distributions as input to the ModelComparisonDistribution</li>
<li>These will be your posteriors from your two models which you are wanting to compare</li>
<li>The first distribution will be raised to the power of (1 - beta) in the posterior, and the second distribution will be raised to the power of beta</li>
<li>(So when beta is equal to 0, the overall posterior will be exactly equal to the first distribution, as it will be raised to the power of 1, while the second distribution will be raised to the power of zero.)</li>
</ul>


<h4>Usage example</h4>
{% highlight xml %}
<distribution
id="posterior"
spec="beast.math.distributions.ModelComparisonDistribution" 
betaParameter="@myBetaParameter">
	<distribution id="posterior_for_first_model"/>
	<distribution id="posterior_for_second_model"/>
</distribution>
{% endhighlight %}



</div>
<div class="beast_object">
<h3><span class='class_path'>beast.core.</span><span class="class_name">ModelComparisonMCMC</span></h3>

This extends BEAST'S usual MCMC chain code to allow for the values of beta to be changed very slowly at every step of the chain.

<h4>Options/Inputs</h4>
<span class="member_name_precursor">name:</span><span class="member_name">betaParameter</span>
<ul>
<li>This must be the same betaParameter which is used by ModelComparisonDistribution as above.</li>
</ul>

<span class="member_name_precursor">name:</span><span class="member_name">betaControlMode</span>
<ul>
<li>This is a text input which determines how the ModelComparison package should control the value of the beta parameter (if at all) so that it slowly changes over the course of the BEAST run</li>
<li>Note:</li>
<li>Before using Model Comparison to control the value of beta, you must first equilibrate your run using one of either 0 or 1</li>
</ul>

Note:<br/>
<ul><li>Many of the same options apply as for usual MCMC (chainLength for example) but not all of them have been tested with ModelComparison</li></ul>

<h4>Usage example</h4>
Instead of using this:
{% highlight xml %}
<run chainLength="20000000" id="mcmc"
spec="beast.core.MCMC">
{% endhighlight %}
Use this:
{% highlight xml %}
<run chainLength="20000000" id="mcmc"
spec="beast.core.ModelComparisonMCMC"
betaParameter="@myBetaParameter">
{% endhighlight %}



</div>
<div class="beast_object">
<h3><span class='class_path'>beast.core.util.</span><span class="class_name">ModelComparisonLogger</span></h3>

This allows for logging the values of beta and U (as defined in the [{{site.lartillot_paper_authors_short}}]({{site.lartillot_paper_link}}) paper).

(??) This seems to have been put in the wrong folder currently...

#### Options/Inputs
- posteriorDistribution
  - This is the ModelComparisonDistribution which is your posterior distribution.

#### Usage


Add the following to any logger (eg. the screenlog or tracelog logger:
{% highlight xml %}
<log spec="util.ModelComparisonLogger" posteriorDistribution="@posterior"/>
<!-- @posterior here means that our posterior object has the id "posterior" -->
{% endhighlight %}
Or alternatively add a new logger which saves only the beta and U values to a file:
{% highlight xml %}
<logger fileName="$(filebase)_beta_U.log" id="betaAndUValueLogger" logEvery="500">
	<log spec="util.ModelComparisonLogger" posteriorDistribution="@posterior"/>
	<!-- Here we have used $(filebase) in the filename - BEAST will replace that part with the filename of your XML file (without the .xml extension) -->
</logger>
{% endhighlight %}

</div>
<div class="beast_object">
<h3><span class='class_path'>beast.app.tools.</span><span class="class_name">ModelComparisonCalculator</span></h3>

<p/>This is a command-line tool for analying the log files outputted with a <i>ModelComparisonLogger</i> included.

<p/>It takes your log file and produces an estimate for the Bayes factor.

</div>



