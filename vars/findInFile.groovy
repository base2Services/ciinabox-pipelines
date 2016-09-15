/***********************************
with Each File Step DSL

find  a block for each file match a pattern

example usage
def environments = findInFile("stringineedtofind") {
  filenameFilter = '*.json'
}
************************************/

def call(pattern, body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def currentDir = new File(config.get('dir','.'))
  def filePattern = config.get('filenameFilter', '.*')
  def matching = []

  echo "looking for ${pattern} in ${currentDir.absolutePath}/"
  new FileNameFinder().getFileNames("${currentDir.absolutePath}/",filePattern).each { filename ->
    def file = new File(filename);
    echo "looking for ${pattern} in ${filename}/"
    if(file.text.contains(pattern)) {
      matching << filename
    }
  }
  matching
}
