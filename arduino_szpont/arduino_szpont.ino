#include <SPI.h>
#include <LoRa.h>
#include <string.h>  // dla memset
#include <ESP8266WiFi.h>

// Konfiguracja pinów LoRa dla Arduino D1 (ESP8266)
#define LORA_CS_PIN 15   // GPIO15 (D8)
#define LORA_RST_PIN 4   // GPIO4 (D2)
#define LORA_DIO0_PIN 5  // GPIO5 (D1)

// Częstotliwość LoRa (433 MHz)
#define LORA_FREQUENCY 433E6

// Konfiguracja WiFi (telefon jako hotspot)
const char* ssid = "THB1234";       // NAZWA hotspotu telefonu
const char* password = "12345678";  // Hasło do hotspotu

// Serwer TCP – zgodny z protokołem aplikacji mobilnej
WiFiServer tcpServer(9000);
WiFiClient tcpClient;
bool tcpClientConnected = false;

// Struktura danych sterowania silnikami (Arduino → Raspberry Pi)
struct MotorControl {
  int sequence;
  int motor_left;
  int motor_right;
};

// Struktura danych z czujników (Raspberry Pi → Arduino)
// Format: '<i f f f f f i i' (little-endian)
// sequence (int), latitude (float), longitude (float),
// angle_x (float), angle_y (float), angle_z (float),
// gps_quality (int), num_satellites (int)
// Razem: 32 bajty
struct SensorData {
  int sequence;
  float latitude;
  float longitude;
  float angle_x;
  float angle_y;
  float angle_z;
  int gps_quality;
  int num_satellites;
};

unsigned long sequence_counter = 0;
unsigned long packets_sent = 0;
unsigned long packets_received = 0;
unsigned long last_motor_seq = 0;

// Wartości silników (domyślnie 5)
int motor_left_value = 5;
int motor_right_value = 5;

// Ostatnie dane z czujników do PA/LI
SensorData lastSensorData;
bool hasLastSensorData = false;

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println();
  Serial.println("========================================");
  Serial.println("ARDUINO SZPONT - Komunikacja z Raspberry Pi");
  Serial.println("========================================");
  Serial.print("MAC Address: ");
  Serial.println(WiFi.macAddress());
  Serial.println("========================================");
  Serial.println();
  
  // Inicjalizacja LoRa
  Serial.println("Inicjalizacja LoRa...");
  LoRa.setPins(LORA_CS_PIN, LORA_RST_PIN, LORA_DIO0_PIN);
  if (!LoRa.begin(LORA_FREQUENCY)) {
    Serial.println("BŁĄD: Nie można zainicjalizować LoRa!");
    Serial.println("Sprawdź połączenia i spróbuj ponownie.");
    while (1);
  }
  
  // Ustaw parametry LoRa (takie same jak Raspberry Pi)
  LoRa.setSpreadingFactor(8);
  LoRa.setSignalBandwidth(125E3);
  LoRa.setCodingRate4(5);
  LoRa.setPreambleLength(8);
  LoRa.setSyncWord(0x34);
  LoRa.enableCrc();
  
  Serial.println("[OK] LoRa zainicjalizowany!");
  Serial.println("Parametry: SF=8, BW=125kHz, CR=4/5, SyncWord=0x34");
  Serial.println();
  
  // Inicjalizacja WiFi
  Serial.println("Łączenie z siecią WiFi (hotspot telefonu)...");
  Serial.print("SSID: ");
  Serial.println(ssid);
  Serial.println("Typ sieci: Otwarta (bez hasła)");
  
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  
  Serial.print("Łączenie");
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  Serial.println();
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.println("========================================");
    Serial.println("Połączono z siecią WiFi!");
    Serial.println("========================================");
    Serial.print("SSID: ");
    Serial.println(ssid);
    Serial.print("IP Address (ESP): ");
    Serial.println(WiFi.localIP());
    Serial.print("MAC Address: ");
    Serial.println(WiFi.macAddress());
    Serial.print("RSSI (sygnał): ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
    Serial.println("========================================");
    
    // Uruchomienie serwera TCP
    tcpServer.begin();
    tcpServer.setNoDelay(true);
    Serial.println();
    Serial.println("Serwer TCP uruchomiony!");
    Serial.print("Połącz aplikację mobilną z: ");
    Serial.print(WiFi.localIP());
    Serial.println(":9000");
    Serial.println("Protokół: GBI/SS/SA/SM/LI (linia zakończona '\\n')");
    Serial.println("========================================");
  } else {
    Serial.println();
    Serial.println("BŁĄD: Nie udało się połączyć z siecią WiFi!");
    Serial.print("Status: ");
    Serial.println(WiFi.status());
    Serial.println("Program będzie działał bez WiFi (tylko LoRa)");
  }
  
  Serial.println();
  Serial.println("Rozpoczynam komunikację...");
  Serial.println();
  delay(1000);
}

void loop() {
  // Obsługa WiFi / serwera TCP (jeśli połączone)
  if (WiFi.status() == WL_CONNECTED) {
    handleTcpServer();
  }
  
  // 1. WYSYŁANIE danych sterowania silnikami do Raspberry Pi
  send_motor_control();
  
  // 2. ODBIERANIE danych z czujników z Raspberry Pi
  receive_sensor_data();
  
  delay(1000); // Odświeżaj co 1 sekundę
}

// ============================================================================
// FUNKCJE – Serwer TCP (protokół jak w socket_test_server.py)
// ============================================================================

void send_boat_information();
void send_position_actualisation_from_last();
void send_sensor_information_from_last();
void handle_tcp_command(const String& line);

void handleTcpServer() {
  // Jeśli nie mamy klienta – spróbuj zaakceptować nowe połączenie
  if (!tcpClientConnected) {
    WiFiClient newClient = tcpServer.available();
    if (newClient) {
      tcpClient = newClient;
      tcpClientConnected = true;
      tcpClient.setTimeout(1000);
      Serial.print("Nowy klient TCP z ");
      Serial.println(tcpClient.remoteIP());

      // Po połączeniu – wyślij informacje o łódce (BI)
      send_boat_information();
    }
    return;
  }

  // Mamy klienta, ale połączenie padło
  if (!tcpClient.connected()) {
    Serial.println("Klient TCP rozłączony");
    tcpClient.stop();
    tcpClientConnected = false;
    return;
  }

  // Czytanie komend (linia po linii, zakończona '\n')
  while (tcpClient.available()) {
    String line = tcpClient.readStringUntil('\n');
    line.trim();
    if (line.length() == 0) continue;

    Serial.print("TCP RECV: ");
    Serial.println(line);
    handle_tcp_command(line);
  }
}

// Wysyła linię tekstu do aplikacji (z '\n')
void tcp_send_line(const String& msg) {
  if (!tcpClientConnected || !tcpClient.connected()) return;
  tcpClient.print(msg);
  tcpClient.print("\n");
  Serial.print("TCP SEND: ");
  Serial.println(msg);
}

// BI: nazwa łódki / kapitan / misja
const char* BOAT_NAME = "ESPBoat";
String currentCaptain = "Unknown";
String currentMission = "-";

void send_boat_information() {
  String response = "BI:";
  response += BOAT_NAME;
  response += ":";
  response += currentCaptain;
  response += ":";
  response += currentMission;
  response += ":BI";
  tcp_send_line(response);
}

// PA:lon:lat:speed:sNum:PA – na podstawie ostatnich danych z LoRa
void send_position_actualisation_from_last() {
  if (!hasLastSensorData) return;

  // Brak prawdziwej prędkości – na razie 0.0 (możesz tu wprowadzić własne wyliczenia)
  float speed = 0.0f;

  String response = "PA:";
  response += String(lastSensorData.longitude, 6);
  response += ":";
  response += String(lastSensorData.latitude, 6);
  response += ":";
  response += String(speed, 2);
  response += ":";
  response += String(lastSensorData.sequence);
  response += ":PA";
  tcp_send_line(response);
}

// SI:magnetic:depth:SI – uproszczone dane czujników dla aplikacji
// Tu używamy angle_z jako "magnetic" (orientacja), a depth = 0.0 (brak realnego sonaru po tej stronie)
void send_sensor_information_from_last() {
  if (!hasLastSensorData) return;

  float magnetic = lastSensorData.angle_z;  // stopnie z osi Z
  float depth = 0.0f;                       // placeholder, jeśli nie masz realnego sonaru po LoRa

  String response = "SI:";
  response += String(magnetic, 1);
  response += ":";
  response += String(depth, 2);
  response += ":SI";
  tcp_send_line(response);
}

// Parsowanie komend z aplikacji
void handle_tcp_command(const String& line) {
  if (line.startsWith("GBI")) {
    // Prośba o Boat Information
    send_boat_information();
    return;
  }

  // Protokół z prefiksem (np. SS:... , SA:..., SM:..., LI:...)
  int firstColon = line.indexOf(':');
  if (firstColon < 0) return;

  String cmd = line.substring(0, firstColon);

  if (cmd == "SS") {
    // Format: SS:left:right:sNum:SS
    // Android wysyła left/right jako double (0.0–1.0)
    int p1 = firstColon + 1;
    int p2 = line.indexOf(':', p1);
    int p3 = p2 >= 0 ? line.indexOf(':', p2 + 1) : -1;

    if (p2 < 0 || p3 < 0) return;

    String leftStr = line.substring(p1, p2);
    String rightStr = line.substring(p2 + 1, p3);
    // sNum jest między p3 a kolejnym ':', ale tutaj go nie wykorzystujemy

    double left = leftStr.toDouble();
    double right = rightStr.toDouble();

    // Mapowanie: jeśli wartości w [0,1] → skala 1–10
    auto mapSpeed = [](double v) -> int {
      if (v <= 1.0) {
        int mapped = (int)round(v * 10.0);
        if (mapped < 1) mapped = 1;
        if (mapped > 10) mapped = 10;
        return mapped;
      }
      // Jeśli wartości już są w 1–10
      int iv = (int)round(v);
      if (iv < 1) iv = 1;
      if (iv > 10) iv = 10;
      return iv;
    };

    motor_left_value = mapSpeed(left);
    motor_right_value = mapSpeed(right);

    Serial.print("Ustawiono z SS - Lewy: ");
    Serial.print(motor_left_value);
    Serial.print(", Prawy: ");
    Serial.println(motor_right_value);

    // Odpowiedź może być poprzez kolejne PA z LoRa
  } else if (cmd == "SA") {
    // Set Action – na razie tylko logujemy
    // SA:action:payload:sNum:SA
    Serial.print("Otrzymano SA: ");
    Serial.println(line);
    // TODO: w razie potrzeby wyślij komendę przez LoRa do Raspberry Pi
  } else if (cmd == "SM") {
    // Set Mission – SM:mission:sNum:SM
    int p1 = firstColon + 1;
    int p2 = line.indexOf(':', p1);
    if (p2 < 0) return;
    String mission = line.substring(p1, p2);
    currentMission = mission;

    Serial.print("Ustawiono misję z SM: ");
    Serial.println(currentMission);

    // TODO: w razie potrzeby wyślij informację o misji do Raspberry Pi po LoRa
  } else if (cmd == "LI") {
    // Lost Information – wyślij ostatnie PA
    Serial.println("Żądanie LI – wysyłam ostatnie PA");
    send_position_actualisation_from_last();
  } else {
    Serial.print("Nieznana komenda TCP: ");
    Serial.println(cmd);
  }
}

void send_motor_control() {
  MotorControl motors;
  
  sequence_counter++;
  motors.sequence = (int)sequence_counter;
  
  // Użyj wartości z suwaków (1-10)
  motors.motor_left = motor_left_value;
  motors.motor_right = motor_right_value;
  
  // Wyślij dane
  LoRa.beginPacket();
  LoRa.write((uint8_t*)&motors, sizeof(MotorControl));
  LoRa.endPacket();
  
  packets_sent++;
  
  Serial.print("[WYSŁANO] ");
  Serial.print("Seq: ");
  Serial.print(motors.sequence);
  Serial.print(", Motor L: ");
  Serial.print(motors.motor_left);
  Serial.print(", Motor R: ");
  Serial.print(motors.motor_right);
  Serial.print(" | Wysłano łącznie: ");
  Serial.println(packets_sent);
}

void receive_sensor_data() {
  // Sprawdź czy są dostępne dane
  int packetSize = LoRa.parsePacket();
  
  if (packetSize > 0) {
    SensorData sensor;
    
    // Wyczyść strukturę
    memset(&sensor, 0, sizeof(SensorData));
    
    // Odbierz dane
    int bytesRead = 0;
    while (LoRa.available() && bytesRead < sizeof(SensorData)) {
      ((uint8_t*)&sensor)[bytesRead] = LoRa.read();
      bytesRead++;
    }
    
    // Sprawdź czy otrzymaliśmy wystarczająco danych (32 bajty)
    if (bytesRead >= sizeof(SensorData)) {
      packets_received++;
      hasLastSensorData = true;
      lastSensorData = sensor;

      // Sprawdź brakujące pakiety
      if (last_motor_seq > 0) {
        long missing = sensor.sequence - last_motor_seq - 1;
        if (missing > 0) {
          Serial.print("[UWAGA] Brakuje ");
          Serial.print(missing);
          Serial.println(" pakietów!");
        }
      }
      last_motor_seq = sensor.sequence;
      
      // Wyświetl wszystkie dane z czujników
      Serial.println();
      Serial.println("========================================");
      Serial.println("DANE Z CZUJNIKÓW (Raspberry Pi)");
      Serial.println("========================================");
      Serial.print("Sekwencja:        ");
      Serial.println(sensor.sequence);
      
      Serial.println();
      Serial.println("--- GPS ---");
      Serial.print("Szerokość:        ");
      Serial.print(sensor.latitude, 5);
      Serial.println("°");
      Serial.print("Długość:          ");
      Serial.print(sensor.longitude, 5);
      Serial.println("°");
      Serial.print("Jakość GPS:       ");
      Serial.print(sensor.gps_quality);
      if (sensor.gps_quality == 0) {
        Serial.println(" (Brak fixa)");
      } else if (sensor.gps_quality == 1) {
        Serial.println(" (Fix GPS)");
      } else if (sensor.gps_quality == 2) {
        Serial.println(" (Fix DGPS)");
      } else {
        Serial.println();
      }
      Serial.print("Liczba satelitów:  ");
      Serial.println(sensor.num_satellites);
      
      Serial.println();
      Serial.println("--- Żyroskop (Odchylenie kąta) ---");
      Serial.print("Oś X:             ");
      Serial.print(sensor.angle_x, 1);
      Serial.println("°");
      Serial.print("Oś Y:             ");
      Serial.print(sensor.angle_y, 1);
      Serial.println("°");
      Serial.print("Oś Z:             ");
      Serial.print(sensor.angle_z, 1);
      Serial.println("°");
      
      Serial.println();
      Serial.print("Odebrano łącznie: ");
      Serial.print(packets_received);
      Serial.print(" pakietów | RSSI: ");
      Serial.print(LoRa.packetRssi());
      Serial.println(" dBm");
      Serial.println("========================================");
      Serial.println();
      
      // Wyślij PA oraz SI do aplikacji (jeśli klient jest podłączony)
      if (tcpClientConnected && tcpClient.connected()) {
        send_position_actualisation_from_last();
        send_sensor_information_from_last();
      }
      
    } else {
      Serial.print("[BŁĄD] Otrzymano za mało danych! ");
      Serial.print("Oczekiwano: ");
      Serial.print(sizeof(SensorData));
      Serial.print(" bajtów, otrzymano: ");
      Serial.print(bytesRead);
      Serial.println(" bajtów");
    }
  }
}

