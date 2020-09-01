def folders = readFileFromWorkspace('pipelines/dirs.txt').split('\n')
folders.each {
  folder(it)
  def pipelines = readFileFromWorkspace("pipelines/${it}.txt").split('\n')
  pipelines.each { p ->
    pipelineJob("${p}") {
      definition {
        cps {
          script(readFileFromWorkspace("pipelines/${p}.groovy"))
          sandbox()
        }
      }
    }
  }
}
