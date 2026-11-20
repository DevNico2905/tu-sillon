# Sillón · imagen de dos etapas (Clase 16)
# La versión de Java (17) debe coincidir con <java.version> del pom.xml.

# Etapa 1: compila el .jar con Maven (sin tests: ya corrieron en el CI antes del merge)
FROM maven:3.9-eclipse-temurin-17 AS compilacion
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# Etapa 2: solo el JRE y el .jar, imagen más liviana
FROM eclipse-temurin:25-jre

# Usuario sin privilegios: si alguien compromete la app, no es root dentro del contenedor
RUN groupadd --system --gid 10001 sillon \
 && useradd --system --uid 10001 --gid sillon --no-create-home --shell /usr/sbin/nologin sillon

WORKDIR /app
# El .jar queda de root y solo lectura para la app: no puede modificarse a sí misma
COPY --from=compilacion /app/target/*.jar app.jar

# UID numérico: plataformas como Kubernetes o ECS pueden verificar que no sea root
USER 10001:10001

EXPOSE 8080
# 75 % de la memoria del contenedor para la JVM.
# ExitOnOutOfMemoryError: si se queda sin memoria, muere y la plataforma la reinicia
# (en vez de quedar viva pero inservible).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
