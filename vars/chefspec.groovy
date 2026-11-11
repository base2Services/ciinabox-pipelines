/***********************************
 chefspec DSL

 Invokes chefspec for a cookbook

 example usage
 chefspec 'cookbook_dir'
 ************************************/

def call(body) {
    withEnv(["COOKBOOK=${body}"]) {
      sh '''#!/bin/bash
      eval "$(/opt/chefdk/bin/chef shell-init bash)"
      export LC_CTYPE=en_US.UTF-8
      echo "=========================================="
      echo "run chef build for cookbook: ${COOKBOOK}"
      echo "=========================================="
      cd $WORKSPACE/$COOKBOOK
      gem install version
      # pinned: newer releases require Ruby >= 3.3 but ChefDK ships Ruby 2.6, so an
      # unpinned install fails and rspec then dies on --format RspecJunitFormatter
      gem install rspec_junit_formatter -v 0.6.2
      berks install
      if [ $? -ne 0 ]; then
        echo "Berkshelf install Failed!"
        exit 2
      fi

      rake test
      if [ $? -ne 0 ]; then
        echo "chefspec failed!"
          exit 2
      fi
      echo "=========================================="
      echo "completed cookbook build ${COOKBOOK}"
      echo "=========================================="
      cd $WORKSPACE
      '''
    }
}
