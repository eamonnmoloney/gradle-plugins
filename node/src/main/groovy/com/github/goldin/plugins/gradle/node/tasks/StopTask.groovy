package com.github.goldin.plugins.gradle.node.tasks

import static com.github.goldin.plugins.gradle.node.NodeConstants.*
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * Stops Node.js application.
 */
class StopTask extends NodeBaseTask
{
    boolean requiresScriptPath(){ ( ! ext.pidOnlyToStop ) }

    @Override
    void taskAction()
    {
        if ( ext.run ) { log{ 'Doing nothing - "run" commands specified' }; return }

        try
        {
            bashExec( stopScript())
            if ( ext.checkAfterStop ) { runTask ( CHECK_STOPPED_TASK )}
        }
        finally
        {
            if ( ext.after ) { bashExec( commandsScript( ext.after ), taskScriptFile( false, true ), false, true, true, false, 'after stop' )}
        }
    }


    @Requires({ ext.pidOnlyToStop || ext.scriptPath })
    @Ensures({ result })
    private String stopScript()
    {
        final pidFilePath = "\$HOME/.forever/pids/${ pidFileName( ext.portNumber ) }"

        """
        |set +e
        |${ listProcesses( false ) }
        |
        |pid=`cat "$pidFilePath"`
        |if [ "\$pid" != "" ];
        |then
        |    echo file:$pidFilePath is found, pid is [\$pid]
        |    foreverId=`${ forever() } list | grep \$pid | awk '{print \$2}' | cut -d[ -f2 | cut -d] -f1`
        |    while [ "\$foreverId" != "" ];
        |    do
        |        echo "Stopping forever process [\$foreverId], pid [\$pid]"
        |        echo ${ forever() } stop \$foreverId
        |        echo
        |        ${ forever() } stop \$foreverId${ ext.removeColorCodes }
        |        foreverId=`${ forever() } list | grep \$pid | awk '{print \$2}' | cut -d[ -f2 | cut -d] -f1`
        |    done
        |else
        |    echo file:$pidFilePath is not found
        |fi
        |
        |${ ext.pidOnlyToStop ? '' : killProcesses() }
        |${ listProcesses() }
        |
        |set -e
        """.stripMargin().toString().trim()
    }
}
