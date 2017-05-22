package com.base2.ciinabox
    
/**
 * Helper method to run docker container with specified image, env vars, volumes, and flags to remove after run or not
 * @param image
 * @param command
 * @param environment
 * @param volumes
 * @param failErrorMessage
 * @param cleanupDockerCommand
 * @param removeAfterRun
 * @return
 */
def dockerRun(image, command, environment = [], volumes = [], failErrorMessage, cleanupDockerCommand, removeAfterRun) {

  def volumesSwitch = '', environmentSwitch = '', failSh = ''

  //generate volumes switch
  if (volumes != null && volumes.size() > 0) {
    def keys = volumes.keySet();
    for (int i = 0; i < volumes.size(); i++) {
      volumesSwitch = volumesSwitch + "-v ${keys[i]}:${volumes[keys[i]]} "
    }
  }

  //generate volumes switch
  if (environment != null && environment.size() > 0) {
    def keys = environment.keySet();
    for (int i = 0; i < environment.size(); i++) {
      environmentSwitch = environmentSwitch + "-e ${keys[i]}=${environment[keys[i]]} "
    }
  }

  if (failErrorMessage == null) failErrorMessage = "Docker image ${image} failed to run with cmd ${command}"

  if (cleanupDockerCommand != null) {
    failSh = "\tdocker run --rm $volumesSwitch $environmentSwitch $image $cleanupDockerCommand"
  }
  failSh = failSh + "\n\techo \"$failErrorMessage\"\n\texit 2"

  def cmd =
          """
          #/bin/bash -xx
          docker run ${removeAfterRun ? '--rm' : ''} $volumesSwitch $environmentSwitch $image $command
          if [ \$? -ne 0 ]; then
              $failSh
          fi
          """

  sh cmd

}