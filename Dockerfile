FROM java:8-jdk
MAINTAINER Shisei Hanai<shanai@jp.ibm.com>

RUN apt-get update
RUN apt-get install unzip -y

RUN useradd -d "/var/home" -s /bin/bash appuser
RUN mkdir -p /opt/functional-store2
ADD target/universal /opt/functional-store2

RUN cd /opt/functional-store2 && \
  cmd=$(basename *.zip .zip) && \
  rm -rf $cmd && \
  unzip $cmd.zip

RUN cd /opt/functional-store2 && \
  cmd=$(basename *.zip .zip) && \
  echo "#!/bin/bash -xe" > launch.sh && \
  echo printenv >> launch.sh && \
  echo "rm -f /opt/functional-store2/$cmd/RUNNING_PID" >> launch.sh && \
  echo "mkdir /opt/functional-store2/$cmd/logs" >> launch.sh && \
  echo "ln -s /opt/functional-store2/$cmd/logs /logs/applog &&:" >> launch.sh && \
  echo "ls -lh /opt/functional-store2/$cmd" >> launch.sh && \
  echo /opt/functional-store2/$cmd/bin/functional-store2 -J-Xmx2048m -DmoduleName=$cmd -DapplyEvolutions.default=true -Dplay.http.secret.key=\${APP_SECRET} >> launch.sh && \
  chmod +x launch.sh

RUN chown -R appuser:appuser /opt/functional-store2
USER appuser

VOLUME ["/logs"]

EXPOSE 9000

ENTRYPOINT ["/bin/bash", "-c", "/opt/functional-store2/launch.sh"]