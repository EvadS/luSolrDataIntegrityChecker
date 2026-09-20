# luSolrCorrectionService

## SonarQube in Docker

Start SonarQube locally:

```bash
docker compose up -d sonarqube
```

SonarQube UI will be available at `http://localhost:9000`.

Default credentials:

- login: `admin`
- password: `admin`

After first login, generate a user token in SonarQube and run analysis:

in my case
windows
mvn clean verify sonar:sonar -Dsonar.projectKey=luSolrDataIntegrityChecker -D"sonar.host.url=http://localhost:9000" -D"sonar.login=sqp_e6a2ecdaca4b11f2ccfc5f504980788baa125ac3"

```bash
mvn clean verify sonar:sonar -Dsonar.token=YOUR_TOKEN
```

```cmd
mvn --% clean verify sonar:sonar -Dsonar.projectKey=luSolrDataIntegrityChecker -Dsonar.host.url=http://localhost:9000 -Dsonar.login=sqp_e6a2ecdaca4b11f2ccfc5f504980788baa125ac3
```

```bash
mvn clean verify sonar:sonar -Dsonar.projectKey="luSolrDataIntegrityChecker" -Dsonar.host.url="http://localhost:9000"  -Dsonar.login="sqp_e6a2ecdaca4b11f2ccfc5f504980788baa125ac3"
```

Project coverage is published from the JaCoCo XML report generated at:

```text
target/site/jacoco/jacoco.xml
```
