package com.base2.ciinabox

import groovy.json.JsonOutput

class PackerTemplateBuilder implements Serializable {

  String id
  String date
  String type
  Boolean winUpdate
  Map builder = [:]
  ArrayList provisioners = []

  PackerTemplateBuilder(String name, String type = 'linux', Boolean winUpdate) {
    this.id = 'bake-' + UUID.randomUUID().toString()
    this.type = type
    this.winUpdate = winUpdate
    def now = new Date()
    this.date = now.format("yyyy-MM-dd't'HH-mm-ss.SSS'z'", TimeZone.getTimeZone('UTC'))
    def amiName = "${name}-${this.date}"

    this.builder.type = 'amazon-ebs'
    this.builder.ami_name = amiName
    this.builder.run_tags = [
      "Status": "Baking",
      "Name": "Packer ${amiName}",
      "BakeId": this.id,
      "Role": name
    ]
    this.builder.tags = [
      "Status": "Baked",
      "Name": amiName,
      "BakeId": this.id,
      "Role": name
    ]
  }

  public void addWindowsUpdate(){
      if (this.type.startsWith('windows')) {
          this.provisioners.push([
              type: 'windows-update'
          ])
      }
  }

  public void addCommunicator(String username) {
    if (this.type.startsWith('windows')) {
      this.builder.communicator = 'winrm'
      this.builder.winrm_username = 'Administrator'
      this.builder.windows_password_timeout = '20m'
      this.builder.user_data_file = 'setup_winrm.ps1'
    } else {
      this.builder.communicator = 'ssh'
      this.builder.ssh_username = username
      this.builder.ssh_pty = true
      this.builder.ssh_timeout = '5m'
    }
  }

  public void addBlockDevice(String deviceName, String volumeSize) {
    def block_device_mapping = [
      device_name: deviceName,
      volume_size: volumeSize,
      delete_on_termination: true,
      volume_type: "gp2"
    ]
    this.builder.ami_block_device_mappings = [block_device_mapping]
    this.builder.launch_block_device_mappings = [block_device_mapping]
  }

  public void addChefSoloProvisioner(List runList, String json, String version, Boolean useCinc=false) {
    def chefProvisioner = [
      type: 'chef-solo',
      chef_license: 'accept-silent',
      run_list: runList
    ]

    if (json) {
      chefProvisioner.json = json
    }
    if (version) {
      chefProvisioner.version = version
    }

    if (this.type.startsWith('windows')) {
      chefProvisioner.remote_cookbook_paths = ["C:/chef/cookbooks"]
      chefProvisioner.guest_os_type = 'windows'
      this.provisioners.push([
        type: 'powershell',
        inline: [
          "New-Item c:/chef/cookbooks -type directory -force",
          "New-Item c:/chef/environments -type directory -force"
        ]
      ])
      if (useCinc) {
        if(version) {
          chefProvisioner.install_command = 'powershell.exe -ExecutionPolicy Bypass -c ". { iwr -useb https://omnitruck.cinc.sh/install.ps1 } | iex; install -version ' + chefProvisioner.version + '"'
        } else {
          chefProvisioner.install_command = 'powershell.exe -ExecutionPolicy Bypass -c ". { iwr -useb https://omnitruck.cinc.sh/install.ps1 } | iex; install"'
        }
        chefProvisioner.execute_command = 'C:/cinc-project/cinc/bin/chef-solo.bat --no-color -c C:/Windows/Temp/packer-chef-solo/solo.rb -j C:/Windows/Temp/packer-chef-solo/node.json'
      }
    } else {
      chefProvisioner.remote_cookbook_paths = ["/etc/chef/cookbooks"]
      this.provisioners.push([
        type: 'shell',
        inline: [
          "sudo mkdir -p /etc/chef",
          "sudo chmod -R 777 /etc/chef"
        ]
      ])
      this.provisioners.push([
        type: 'file',
        source: '{{pwd}}/cookbooks',
        destination: '/etc/chef'
      ])
      this.provisioners.push([
        type: 'file',
        source: '{{pwd}}/environments',
        destination: '/etc/chef'
      ])
      if (useCinc) {
        chefProvisioner.install_command = "curl -L https://omnitruck.cinc.sh/install.sh | sudo bash -s -- -v " + chefProvisioner.version
      }
    }

    this.provisioners.push(chefProvisioner)
  }

  public void addDownloadCookbookProvisioner(String bucket, String region, String path, String script = 'download_cookbooks.ps1') {
    if (this.type.startsWith('windows')) {
      Map provisioner = [
        type: 'powershell',
        script: script,
        environment_vars: [
          "SOURCE_BUCKET=${bucket}",
          "BUCKET_REGION=${region}",
          "COOKBOOK_PATH=${path}"
        ]
      ]
      this.provisioners.push(provisioner)
    }
  }

  public void addInstall7zipProvisioner(String script = 'install_7zip.ps1') {
    if (this.type.startsWith('windows')) {
      Map provisioner = [
        type: 'powershell',
        script: script
      ]
      this.provisioners.push(provisioner)
    }
  }

  public void addAmamzonConfigProvisioner() {
    if (this.type.equals('windows')) {
      this.provisioners.push([
        type: 'powershell',
        script: 'ec2_launch_config.ps1'
      ])
      this.provisioners.push([
        type: 'powershell',
        inline: ['C:/ProgramData/Amazon/EC2-Windows/Launch/Scripts/InitializeInstance.ps1 -Schedule']
      ])
    } else if (this.type.equals('windows2012')) {
      this.provisioners.push([
        type: 'powershell',
        script: 'ec2_config_service.ps1'
      ])
    }

  }

  public String toJson() {
    Map template = [
      builders: [],
      provisioners: [],
      "post-processors": [
        [
          type: 'manifest',
          output: "${this.id}.manifest.json",
          strip_path: true
        ]
      ]
    ]

    template.builders.push(this.builder)
    template.provisioners = this.provisioners

    return JsonOutput.prettyPrint(JsonOutput.toJson(template))
  }
}
