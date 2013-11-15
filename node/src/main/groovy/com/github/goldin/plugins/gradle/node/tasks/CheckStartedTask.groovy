package com.github.goldin.plugins.gradle.node.tasks

import static com.github.goldin.plugins.gradle.node.NodeConstants.*
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel


/**
 * Checks that Node.js application is up and running.
 */
class CheckStartedTask extends NodeBaseTask
{
    @Override
    void taskAction()
    {
        def success = false
        def time = 0
        String logMessage = null
        String responseContent = null
        String checkContent = null
        int checkStatusCode = 0
        while(!success && time < ext.checkWait){
            ext.checks.each {
                String checkUrl, List<?> list ->

                assert checkUrl && list && ( list.size() == 2 )

                final checkPort          = ( ext.publicIp ? checkUrl.find( /:(\d+)/ ){ it[1] } ?: ''                                      : '' )
                final checkPath          = ( ext.publicIp ? checkUrl.replaceFirst( ~'https?://[^/]+', '' )                                : '' )
                final publicUrl          = ( ext.publicIp ? "http://${ ext.publicIp }${ checkPort ? ':' + checkPort : '' }${ checkPath }" : '' )
                checkStatusCode          = list[ 0 ] as int
                checkContent             = list[ 1 ] as String

                final response           = httpRequest( checkUrl, 'GET', [:], ext.checkTimeout * 500, ext.checkTimeout * 500, false )
                final responseStatusCode = response.statusCode
                responseContent          = response.asString()
                final isGoodResponse     = ( responseStatusCode == checkStatusCode ) && contentMatches( responseContent, checkContent, '*' )
                logMessage               = "Connecting to $checkUrl${ publicUrl ? ' (' + publicUrl + ')' : '' } resulted in " +
                                           (( responseStatusCode instanceof Integer ) ? "status code [$responseStatusCode]" :
                                                                                        "'$responseStatusCode'" ) //  If not Integer then it's an error
                if ( isGoodResponse )
                {
                    log{ "$logMessage${ checkContent ? ', content contains [' + checkContent + ']' : '' } - good!" }
                    success = true
                }
            }
            sleepMs( 1000 )
            time++
        }

        if(!success){
            fail(logMessage, responseContent, checkStatusCode, checkContent)
        }
    }

    void fail(logMessage, responseContent, checkStatusCode, checkContent){
        final displayLogStep = 'display application log'
        final errorDetails   = "$logMessage, content [$responseContent] while we expected status code [$checkStatusCode]" +
                               ( checkContent ? ", content contains [$checkContent]" : '' ) +
                               ". See '$displayLogStep'."
        final errorMessage   = """
                               |-----------------------------------------------------------
                               |  -=-= The application has failed to start properly! =-=-
                               |-----------------------------------------------------------
                               |$errorDetails
                               """.stripMargin()

        log( LogLevel.ERROR ) { errorMessage }

        runTask( LIST_TASK )
        shellExec( tailLogScript(), baseScript( displayLogStep ), scriptFileForTask( 'tail-log' ), false, false, true, false, LogLevel.ERROR )

        if ( ext.stopIfFailsToStart ){ runTask( STOP_TASK )}

        throw new GradleException( errorMessage )
    }


    @SuppressWarnings([ 'LineLength' ])
    String tailLogScript()
    {
        // Sorting "forever list" output by processes uptime, taking first element with a minimal uptime and listing its log.
        """
        |echo $LOG_DELIMITER
        |${ forever() } logs `${ forever() } list | $REMOVE_COLOR_CODES | grep -E '\\[[0-9]+\\]' | awk '{print \$NF,\$2}' | sort -n | head -1 | awk '{print \$2}' | tr -d '[]'`${ ext.removeColorCodes }
        |echo $LOG_DELIMITER
        """.stripMargin().toString().trim()
    }
}
