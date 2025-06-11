# PromoOTP

PromoOTP — это серверное Java-приложение для генерации и валидации одноразовых паролей (OTP) с поддержкой различных каналов доставки: email, SMS, Telegram и файловая система.

## Возможности

- Регистрация и аутентификация пользователей (ADMIN/USER)
- Генерация OTP для операций пользователя
- Валидация OTP
- Настройка длины и времени жизни OTP (только для администратора)
- Отправка OTP через email, SMS, Telegram или сохранение в файл
- Администрирование пользователей (просмотр, удаление)
- Автоматическая очистка устаревших OTP

## Технологии и зависимости

- Java 17
- PostgreSQL 17
- Maven
- HikariCP (пул соединений)
- SLF4J (логирование)
- JJWT (JWT-токены)
- Jackson (JSON)
- javax.mail (email)
- OpenSMPP (SMS)
- Apache HttpClient (Telegram)
- Flyway (миграции БД)

## Быстрый старт

1. **Клонируйте репозиторий и перейдите в папку проекта:**
   ```
   git clone <repo-url>
   cd PromoOTP
   ```

2. **Настройте PostgreSQL 17 и создайте базу данных:**
   ```
   createdb otpdb
   ```

3. **Настройте параметры в `src/main/resources/application.properties` и другие конфиги (`email.properties`, `sms.properties`, `telegram.properties`).**

4. **Соберите проект:**
   ```
   mvn clean package
   ```

5. **Запустите приложение:**
   ```
   java -jar target/PromoOTP-1.0-SNAPSHOT.jar
   ```

6. **API будет доступен на порту 8080 (или указанном в настройках).**

## Примеры API

- `POST /api/register` — регистрация пользователя
- `POST /api/login` — получение JWT токена
- `POST /api/user/generateOtp` — генерация OTP
- `POST /api/user/validateOtp` — валидация OTP
- `POST /api/admin/config` — изменение настроек OTP (только для ADMIN)
- `GET /api/admin/users` — список пользователей (только для ADMIN)
- `POST /api/admin/deleteUser` — удаление пользователя (только для ADMIN)

## Переменные окружения

- `DB_USERNAME`, `DB_PASSWORD` — для подключения к БД
- `JWT_SECRET`, `JWT_EXPIRATION_MS` — для JWT
- `SERVER_PORT`, `THREAD_POOL_SIZE` — для сервера

## Примечания

- Для работы email, SMS и Telegram уведомлений требуется корректная настройка соответствующих файлов конфигурации.
- Для production обязательно задайте свой `JWT_SECRET` через переменные окружения.

---
