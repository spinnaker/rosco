FROM openjdk:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew buildDeb -x test && \
  dpkg -i ./rosco-web/build/distributions/*.deb && \
  mkdir /packer && \
  cd /packer && \
  wget https://releases.hashicorp.com/packer/1.2.2/packer_1.2.2linux_amd64.zip && \
  apt-get install unzip -y && \
  unzip packer_1.2.2_linux_amd64.zip && \
  rm -rf /workdir

ENV PATH "/packer:$PATH"

CMD ["/opt/rosco/bin/rosco"]
