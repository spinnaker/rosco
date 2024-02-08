#!/bin/sh

# Add /usr/local/bin to path so helm and helm3 can be found,
# otherwise their installation returns a non-zero exit code
# causing this script to fail,
PATH=$PATH:/usr/local/bin

# ubuntu
# check that owner group exists
if [ -z `getent group spinnaker` ]; then
  groupadd spinnaker
fi

# check that user exists
if [ -z `getent passwd spinnaker` ]; then
  useradd --gid spinnaker spinnaker -m --home-dir /home/spinnaker
fi

ARCH=$(uname -m)
if [ "$ARCH" = "x86_64" ]; then
  ARCH="amd64"
fi

# Get the PACKER_PLUGINS environment variable or set a default value
PACKER_PLUGINS=${PACKER_PLUGINS:-"amazon azure googlecompute"}

create_temp_dir() {
  TEMPDIR=$(mktemp -d /tmp/installrosco.XXXX)
  cd $TEMPDIR
}

remove_temp_dir() {
  cd ..
  rm -rf $TEMPDIR
}

install_packer() {
  PACKER_VERSION="1.10.1"
  local packer_version="$(/usr/bin/packer --version)"
  local packer_status=$?
  if [ $packer_status -ne 0 ] || [ "$packer_version" != "$PACKER_VERSION" ]; then
    wget https://releases.hashicorp.com/packer/${PACKER_VERSION}/packer_${PACKER_VERSION}_linux_${ARCH}.zip
    unzip -o "packer_${PACKER_VERSION}_linux_amd64.zip" -d /usr/bin
  fi

  # Install plugins as the "spinnaker" user
  for plugin in $PACKER_PLUGINS; do
    su - spinnaker -c "/usr/bin/packer plugins install \"github.com/hashicorp/$plugin\""
  done
}

install_helm() {
  wget https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get
  chmod +x get
  ./get --version v2.17.0
  rm get
}

install_helm3() {
  wget https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get-helm-3
  chmod +x get-helm-3
  ./get-helm-3
  rm get-helm-3
  mv /usr/local/bin/helm /usr/local/bin/helm3
}

install_aws_cli2() {
  if [ "$ARCH" = "amd64" ]; then
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    curl "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_64bit/session-manager-plugin.deb" -o "session-manager-plugin.deb"
  elif [ "$ARCH" = "arm64" ]; then
    curl "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "awscliv2.zip"
    curl "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_arm64/session-manager-plugin.deb" -o "session-manager-plugin.deb"
  fi
  unzip awscliv2.zip
  ./aws/install
  rm -rf awscliv2.zip ./aws
  dpkg -i session-manager-plugin.deb
  rm session-manager-plugin.deb
}

create_temp_dir
install_packer
install_helm3
install_helm
install_aws_cli2
remove_temp_dir
install --mode=755 --owner=spinnaker --group=spinnaker --directory  /var/log/spinnaker/rosco
