package org.apache.maven.plugins.enforcer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import org.apache.maven.enforcer.rule.api.EnforcerLevel;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRule2;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.path.PathTranslator;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;

/**
 * This goal executes the defined enforcer-rules once per
 * module.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @version $Id$
 */
@Mojo( name = "enforce", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true )
public class EnforceMojo
    extends AbstractEnforceMojo
{
    /**
     * Flag to fail the build if a version check fails.
     */
    @Parameter(property = "enforcer.fail", defaultValue = "true")
    private boolean fail = true;

    /**
     * Fail on the first rule that doesn't pass
     */
    @Parameter(property = "enforcer.failFast", defaultValue = "false")
    private boolean failFast = false;

    /**
     * Array of objects that implement the EnforcerRule
     * interface to execute.
     */
    @Parameter( required = true )
    private EnforcerRule[] rules;

    /**
     * Use this flag to disable rule result caching. This will cause
     * all rules to execute on each project even if the rule indicates it can
     * safely be cached.
     */
    @Parameter( property = "enforcer.ignoreCache", defaultValue = "false" )
    protected boolean ignoreCache = false;

    /**
     * This is a static variable used to persist the cached results across plugin invocations.
     */
    protected static Hashtable<String, EnforcerRule> cache = new Hashtable<String, EnforcerRule>();


    // set by the contextualize method. Only way to get the
    // plugin's container in 2.0.x
    protected PlexusContainer container;

    public void contextualize ( Context context )
        throws ContextException
    {
        container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }

    /**
     * Entry point to the mojo
     * @throws MojoExecutionException
     */
    public void execute ()
        throws MojoExecutionException
    {
        Log log = this.getLog();

        EnforcerExpressionEvaluator evaluator = new EnforcerExpressionEvaluator( session, translator, project );

        // the entire execution can be easily skipped
        if ( !skip )
        {
            // list to store exceptions
            List<String> list = new ArrayList<String>();

            // make sure the rules exist
            if ( rules != null && rules.length > 0 )
            {
                String currentRule = "Unknown";

                // create my helper
                EnforcerRuleHelper helper = new DefaultEnforcementRuleHelper( session, evaluator, log, container );

                // if we are only warning, then disable
                // failFast
                if ( !fail )
                {
                    failFast = false;
                }

                boolean hasErrors = false;

                // go through each rule
                for ( int i = 0; i < rules.length; i++ )
                {

                    // prevent against empty rules
                    EnforcerRule rule = rules[i];
                    final EnforcerLevel level = getLevel( rule );
                    if ( rule != null )
                    {
                        // store the current rule for
                        // logging purposes
                        currentRule = rule.getClass().getName();
                        log.debug( "Executing rule: " + currentRule );
                        try
                        {
                            if ( ignoreCache || shouldExecute( rule ) )
                            {
                                // execute the rule
                                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                                synchronized ( rule )
                                {
                                   rule.execute( helper );
                                }
                            }
                        }
                        catch ( EnforcerRuleException e )
                        {
                            // i can throw an exception
                            // because failfast will be
                            // false if fail is false.
                            if ( failFast && level == EnforcerLevel.ERROR )
                            {
                                throw new MojoExecutionException( currentRule + " failed with message:\n"
                                    + e.getMessage(), e );
                            }
                            else
                            {
                                if (level == EnforcerLevel.ERROR) {
                                    hasErrors = true;
                                    list.add( "Rule " + i + ": " + currentRule + " failed with message:\n" + e.getMessage() );
                                    log.debug( "Adding failure due to exception" , e );
                                }
                                else
                                {
                                    list.add( "Rule " + i + ": " + currentRule + " warned with message:\n" + e.getMessage() );
                                    log.debug( "Adding warning due to exception" , e );
                                }
                            }
                        }
                    }
                }

                // if we found anything
                if ( !list.isEmpty() )
                {
                    for ( String failure  : list )
                    {
                        log.warn( failure );
                    }
                    if ( fail && hasErrors )
                    {
                        throw new MojoExecutionException(
                                                          "Some Enforcer rules have failed. Look above for specific messages explaining why the rule failed." );
                    }
                }
            }
            else
            {
                throw new MojoExecutionException(
                                                  "No rules are configured. Use the skip flag if you want to disable execution." );
            }
        }
        else
        {
            log.info( "Skipping Rule Enforcement." );
        }
    }

    /**
     * This method determines if a rule should execute based
     * on the cache
     *
     * @param rule the rule to verify
     * @return {@code true} if rule should be executed, otherwise {@code false}
     */
    protected boolean shouldExecute ( EnforcerRule rule )
    {
        if ( rule.isCacheable() )
        {
            Log log = this.getLog();
            log.debug( "Rule " + rule.getClass().getName() + " is cacheable." );
            String key = rule.getClass().getName() + " " + rule.getCacheId();
            if ( EnforceMojo.cache.containsKey( key ) )
            {
                log.debug( "Key " + key + " was found in the cache" );
                if ( rule.isResultValid( (EnforcerRule) cache.get( key ) ) )
                {
                    log.debug( "The cached results are still valid. Skipping the rule: " + rule.getClass().getName() );
                    return false;
                }
            }

            //add it to the cache of executed rules
            EnforceMojo.cache.put( key, rule );
        }
        return true;
    }

    /**
     * @return the fail
     */
    public boolean isFail ()
    {
        return this.fail;
    }

    /**
     * @param theFail the fail to set
     */
    public void setFail ( boolean theFail )
    {
        this.fail = theFail;
    }

    /**
     * @return the rules
     */
    @Override
    public EnforcerRule[] getRules ()
    {
        return this.rules;
    }

    /**
     * @param theRules the rules to set
     */
    @Override
    public void setRules ( EnforcerRule[] theRules )
    {
        this.rules = theRules;
    }

    /**
     * @param theFailFast the failFast to set
     */
    @Override
    public void setFailFast ( boolean theFailFast )
    {
        this.failFast = theFailFast;
    }

    @Override
    public boolean isFailFast() {
        return failFast;
    }


    @Override
    protected String createRuleMessage( int i , String currentRule , EnforcerRuleException e )
    {
        return "Rule " + i + ": " + currentRule + " failed with message:\n" + e.getMessage();
    }

    /**
     * @param theTranslator the translator to set
     */
    public void setTranslator ( PathTranslator theTranslator )
    {
        this.translator = theTranslator;
    }

    /**
     * Returns the level of the rule, defaults to {@link EnforcerLevel#ERROR}
     * for backwards compatibility.
     *
     * @param rule might be of type {@link EnforcerRule2}.
     * @return level of the rule.
     */
    private EnforcerLevel getLevel( EnforcerRule rule )
    {
        if ( rule instanceof EnforcerRule2 )
        {
            return ( (EnforcerRule2) rule ).getLevel();
        }
        else
        {
            return EnforcerLevel.ERROR;
        }
    }
}
