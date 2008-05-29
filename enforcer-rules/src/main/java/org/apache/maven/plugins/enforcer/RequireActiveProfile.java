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
package org.apache.maven.plugins.enforcer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a> This rule checks that the current project is not a snapshot
 */
public class RequireActiveProfile
    extends AbstractStandardEnforcerRule
{

    /**
     * Comma separated list of profiles to check
     */
    public String profiles = null;

    /**
     * If all profiles must be active. If false, only one must be active
     */
    public boolean all = true;

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.enforcer.rule.api.EnforcerRule#execute(org.apache.maven.enforcer.rule.api.EnforcerRuleHelper)
     */
    public void execute( EnforcerRuleHelper theHelper )
        throws EnforcerRuleException
    {
        List missingProfiles = new ArrayList();
        try
        {
            MavenProject project = (MavenProject) theHelper.evaluate( "${project}" );
            if ( StringUtils.isNotEmpty( profiles ) )
            {
                String[] profs = profiles.split( "," );
                for ( int i = 0; i < profs.length; i++ )
                {
                    if ( !isProfileActive( project, profs[i] ) )
                    {
                        missingProfiles.add( profs[i] );
                    }
                }

                boolean fail = false;
                if ( !missingProfiles.isEmpty() )
                {
                    fail = true;
                    // if (all && missingProfiles.size() != profs.length)
                    // {
                    // fail = true;
                    // }
                    // else
                    // {
                    // if (!all && missingProfiles.size() >= (profs.length -1))
                    // {
                    // fail = true;
                    // }
                    // }
                }

                if ( fail )
                {
                    StringBuffer buf = new StringBuffer();
                    if ( message != null )
                    {
                        buf.append( message + "\n" );
                    }

                    Iterator iter = missingProfiles.iterator();
                    while ( iter.hasNext() )
                    {
                        buf.append( "Profile \"" + iter.next().toString() + "\" is not activated.\n" );
                    }

                    throw new EnforcerRuleException( buf.toString() );
                }

            }

        }
        catch ( ExpressionEvaluationException e )
        {
            throw new EnforcerRuleException( "Unable to retrieve the project.", e );
        }

    }

    protected boolean isProfileActive( MavenProject project, String profileName )
    {
        List activeProfiles = project.getActiveProfiles();
        if ( activeProfiles != null && !activeProfiles.isEmpty() )
        {
            for ( Iterator it = activeProfiles.iterator(); it.hasNext(); )
            {
                Profile profile = (Profile) it.next();
                if ( profile.getId().equals( profileName ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.enforcer.rule.api.EnforcerRule#getCacheId()
     */
    public String getCacheId()
    {
        return "0";
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.enforcer.rule.api.EnforcerRule#isCacheable()
     */
    public boolean isCacheable()
    {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.enforcer.rule.api.EnforcerRule#isResultValid(org.apache.maven.enforcer.rule.api.EnforcerRule)
     */
    public boolean isResultValid( EnforcerRule theCachedRule )
    {
        return false;
    }

}
