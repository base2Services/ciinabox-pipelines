package com.base2.ciinabox

/**
 * As key sets returned by @see java.util.List.keySet() are not serializable, use
 * this Non-CPS method to retrieve mapKeys within pipeline steps
 * @param map @see java.util.List of keys for given Map.
 * @return
 */
@NonCPS
def mapKeys(map) {
  def keySet = map.keySet(), keysArray = new String[keySet.size()]
  for (int i = 0; i < keySet.size(); i++) {
    keysArray[i] = keySet[i]
  }
  keySet = null
  return keysArray
}

/**
 *
 * @param stepsMap @see java.util.Map of steps prepared to be executed using pipeline
 *        parallel statement. If parallel jobs are to be executed in batches (rather than all
 *         at the same time). Size of one batch can be controlled via @see partitionSize param
 * @param partitionSize Size of single batch of jobs to be executed using parallel statement in pipelines
 * @return
 */
@NonCPS
def partitionSteps(stepsMap, partitionSize) {
  def currentPartition = [:],
      partitions = [],
      keys = stepsMap.keySet()
  for (int i = 0; i < keys.size(); i++) {
    def key = keys[i]

    if (i % partitionSize == 0) {
      currentPartition = [:]
      partitions << currentPartition
    }

    currentPartition[key] = stepsMap[key]
  }


  return partitions
}

/**
 * Execute list of @see groovy.lang.Closure in series
 * @param closures
 */
def runJobClosures(closures) {
  for (int i = 0; i < closures.size(); i++) {
    closures[i].call()
  }
}
