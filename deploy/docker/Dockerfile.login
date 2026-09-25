# L2 NewEra LoginServer image
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache bash gettext util-linux \
    && addgroup -S brproject && adduser -S brproject -G brproject

WORKDIR /l2Brproject

# Runtime jars + login tree
COPY --chown=brproject:brproject libs/ /l2Brproject/libs/
COPY --chown=brproject:brproject login/ /l2Brproject/login/
COPY --chown=brproject:brproject deploy/docker/run-login.sh /l2Brproject/run-login.sh
COPY --chown=brproject:brproject deploy/docker/config/login/loginserver.properties.template /l2Brproject/login/config/loginserver.properties.template
COPY --chown=brproject:brproject deploy/docker/config/hexid.txt.template /l2Brproject/login/config/hexid.txt.template

RUN sed -i 's/\r$//' /l2Brproject/run-login.sh /l2Brproject/login/config/loginserver.properties.template /l2Brproject/login/config/hexid.txt.template \
    && chmod +x /l2Brproject/run-login.sh \
    && mkdir -p /l2Brproject/login/log \
    && chown -R brproject:brproject /l2Brproject

USER brproject
EXPOSE 2106 9014
ENTRYPOINT ["/l2Brproject/run-login.sh"]
