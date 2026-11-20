Copia **solo para los tests** de las migraciones del catálogo del monolito (`src/main/resources/db/migration`
V1.0, V1.1 y V1.5). El servicio no corre Flyway: el dueño del esquema y de los datos es el monolito hasta que el
servicio tenga su propia base (etapa 7 del plan de extracción). Si el monolito agrega una migración del catálogo, hay
que copiarla acá.
