FROM docker:27-cli

RUN apk add --no-cache bash coreutils findutils git jq docker-cli-compose

WORKDIR /workspace
ENTRYPOINT ["bash", "/workspace/deploy/updater/update-agent.sh"]
