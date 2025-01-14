###########################################################################################
# The core rosco libs image, can be used for executing rosco jobs as k8s run jobs.
# Doesn't contain the rosco service itself, but rather the libs rosco uses to do bakes.
# docker build -f Dockerfile.remote --target rosco-remote-core -t armory/rosco-remote-core .
###########################################################################################
FROM ubuntu:22.04 as rosco-remote-core

MAINTAINER spinnaker@spinnaker.io

ENV KUSTOMIZE_VERSION=3.8.6
ENV KUSTOMIZE4_VERSION=4.5.5
ENV TARGETARCH=amd64

WORKDIR /packer

ENV TZ=UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN apt update && apt upgrade -y && apt install -y bash wget curl openssl unzip git openssh-client
# TODO: Support arm.
ENV PACKER_VERSION=1.8.1
RUN wget --quiet "https://releases.hashicorp.com/packer/${PACKER_VERSION}/packer_${PACKER_VERSION}_linux_${TARGETARCH}.zip"
RUN unzip packer_${PACKER_VERSION}_linux_${TARGETARCH}.zip
RUN rm packer_${PACKER_VERSION}_linux_${TARGETARCH}.zip
RUN unset PACKER_VERSION

ENV PATH "/packer:$PATH"

# Install Helm 3
RUN wget --quiet https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get-helm-3 && \
  chmod +x get-helm-3 && \
  ./get-helm-3 && \
  rm get-helm-3 && \
  mv /usr/local/bin/helm /usr/local/bin/helm3

# Install Helm 2
RUN wget --quiet https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get && \
  chmod +x get && \
  ./get --version v2.17.0 && \
  rm get

RUN mkdir kustomize && \
  curl -s -L https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize/v${KUSTOMIZE_VERSION}/kustomize_v${KUSTOMIZE_VERSION}_linux_${TARGETARCH}.tar.gz|\
  tar xvz -C kustomize/ && \
  mv ./kustomize/kustomize /usr/local/bin/kustomize && \
  rm -rf ./kustomize

RUN mkdir kustomize && \
  curl -s -L https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize/v${KUSTOMIZE4_VERSION}/kustomize_v${KUSTOMIZE4_VERSION}_linux_${TARGETARCH}.tar.gz|\
  tar xvz -C kustomize/ && \
  mv ./kustomize/kustomize /usr/local/bin/kustomize4 && \
  rm -rf ./kustomize

# Install the AWS SSM Agent for baking AMIs in private VPCs
RUN wget --quiet https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_64bit/session-manager-plugin.deb
RUN dpkg -i session-manager-plugin.deb
RUN rm session-manager-plugin.deb

###########################################################################################
# The rosco image for executing jobs in fargate
# docker build -f Dockerfile.remote --target rosco-remote-fargate-job -t armory/rosco-remote-fargate-job .
###########################################################################################
FROM rosco-remote-core as rosco-remote-fargate-job

RUN apt update && apt install -y jq nodejs

# Install Vault for fetching remote rosco job contexts securly
WORKDIR /opt/vault
ENV VAULT_VERSION=1.5.5
RUN wget --quiet https://releases.hashicorp.com/vault/${VAULT_VERSION}/vault_${VAULT_VERSION}_linux_${TARGETARCH}.zip
RUN unzip vault_${VAULT_VERSION}_linux_${TARGETARCH}.zip
RUN chmod +x ./vault
RUN rm vault_${VAULT_VERSION}_linux_${TARGETARCH}.zip
ENV PATH "/opt/vault:$PATH"

# Install the wrapper scripts
WORKDIR /opt/rosco-job
COPY rosco-fargate-job-executor/docker/scripts/fargate-rosco-command-wrapper.sh /opt/rosco-job
COPY rosco-fargate-job-executor/docker/scripts/unpack-job-context.js /opt/rosco-job
RUN chmod +x /opt/rosco-job/fargate-rosco-command-wrapper.sh
RUN chmod +x /opt/rosco-job/unpack-job-context.js

# Set the default cmd
CMD ["/opt/rosco-job/fargate-rosco-command-wrapper.sh"]
