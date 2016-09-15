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

  def currentDir = config.get('dir',pwd())
  def filePattern = config.get('filenameFilter', '.*')
  def matching = []

  sh "printenv && ls -al ${currentDir}"
  echo "looking for ${pattern} in ${currentDir}/${filePattern}"
  new FileNameFinder().getFileNames("${currentDir}",filePattern).each { filename ->
    def file = new File(filename);
    echo "looking for ${pattern} in ${filename}/"
    if(file.text.contains(pattern)) {
      matching << filename
    }
  }
  matching
}
