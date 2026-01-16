#include <SPI.h>
#include <LoRa.h>
#include <string.h>  // dla memset
#include <math.h>    // dla sin, cos, atan2, sqrt (obliczanie prędkości GPS)
#include <ESP8266WiFi.h>

// Konfiguracja pinów LoRa dla Arduino D1 (ESP8266)
#define LORA_CS_PIN 15   // GPIO15 (D8)
#define LORA_RST_PIN 4   // GPIO4 (D2)
#define LORA_DIO0_PIN 5  // GPIO5 (D1)

// Definicja PI jeśli nie jest dostępna
#ifndef PI
#define PI 3.14159265358979323846
#endif

// Częstotliwość LoRa (433 MHz)
#define LORA_FREQUENCY 433E6

// Konfiguracja WiFi (telefon jako hotspot)
const char* ssid = "THB1234";       // NAZWA hotspotu telefonu
const char* password = "12345678";  // Hasło do hotspotu

// Serwer TCP – zgodny z protokołem aplikacji mobilnej
WiFiServer tcpServer(9000);
WiFiClient tcpClient;
bool tcpClientConnected = false;

// Protokół tekstowy - komunikacja przez LoRa w formacie tekstowym
// Format wiadomości: KOD:param1:param2:...:KOD

unsigned long sequence_counter = 0;
unsigned long packets_sent = 0;
unsigned long packets_received = 0;
unsigned long last_motor_seq = 0;

// Wartości silników (domyślnie 0 = neutral/stop)
int motor_left_value = 0;
int motor_right_value = 0;

// Stan silnika zwijarki: 0 = góra (up), 1 = wyłączony (stop), 2 = dół (down)
int winch_state = 1; // Domyślnie wyłączony

// Ostatnie dane z czujników do PA/LI/SI
struct LastSensorData {
  float latitude;
  float longitude;
  float speed_ms;
  int gps_quality;
  int num_satellites;
  String sensor_info;  // Pełna wiadomość SI
  unsigned long sequence;
} lastSensorData;

bool hasLastSensorData = false;

// Prędkość jest już obliczana i wysyłana przez Raspberry Pi w wiadomości PA

// Walidacja danych GPS - sprawdza czy wartości są sensowne
// ZMIENIONE: Pozwalamy na 0.0, 0.0 (GPS bez fixa) - aplikacja może to wyświetlić
bool isValidGpsData(float lat, float lon) {
  // Sprawdź czy wartości są w prawidłowym zakresie geograficznym
  if (lat < -90.0 || lat > 90.0) return false;
  if (lon < -180.0 || lon > 180.0) return false;
  
  // POZWALAMY na 0.0, 0.0 - aplikacja może to wyświetlić jako "brak fixa GPS"
  // (pozycja 0.0, 0.0 to ocean na zachód od Afryki)
  
  return true;
}

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
  
  // Upewnij się, że LoRa jest w trybie RX
  LoRa.receive();
  Serial.println("[INIT] LoRa ustawiony w tryb RX (odbiór)");
  
  delay(1000);
}

void loop() {
  // Obsługa WiFi / serwera TCP (jeśli połączone)
  if (WiFi.status() == WL_CONNECTED) {
    handleTcpServer();
  }
  
  // 1. ODBIERANIE danych z czujników z Raspberry Pi (NAJPIERW - musimy być w RX)
  receive_sensor_data();
  
  // 2. WYSYŁANIE danych sterowania silnikami do Raspberry Pi
  send_motor_control();
  
  // 3. Wróć do trybu RX po wysłaniu
  LoRa.receive();
  
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

    // Parsuj nagłówek dla czytelnego komunikatu
    int firstColon = line.indexOf(':');
    String header = firstColon > 0 ? line.substring(0, firstColon) : line;
    Serial.print("[ODEBRANO][TCP][");
    Serial.print(header);
    Serial.print("] ");
    Serial.println(line);
    
    handle_tcp_command(line);
  }
}

// Wysyła linię tekstu do aplikacji (z '\n')
void tcp_send_line(const String& msg) {
  if (!tcpClientConnected || !tcpClient.connected()) return;
  tcpClient.print(msg);
  tcpClient.print("\n");
  
  // Parsuj nagłówek dla czytelnego komunikatu
  int firstColon = msg.indexOf(':');
  String header = firstColon > 0 ? msg.substring(0, firstColon) : msg;
  Serial.print("[NADANO][TCP][");
  Serial.print(header);
  Serial.print("] ");
  Serial.println(msg);
}

// BI: nazwa łódki / kapitan / misja
const char* BOAT_NAME = "szpont";
String currentCaptain = "1";
String currentMission = "default";

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
  if (!hasLastSensorData) {
    Serial.println("[PA] Brak danych z czujników - nie wysyłam PA");
    return;
  }

  // Walidacja danych GPS (sprawdza tylko zakres, pozwala na 0.0, 0.0)
  if (!isValidGpsData(lastSensorData.latitude, lastSensorData.longitude)) {
    Serial.print("[PA] Nieprawidłowe dane GPS (poza zakresem): lat=");
    Serial.print(lastSensorData.latitude, 5);
    Serial.print(", lon=");
    Serial.print(lastSensorData.longitude, 5);
    Serial.println(" - nie wysyłam PA");
    return;
  }
  
  // Loguj informację o GPS bez fixa (0.0, 0.0)
  if (lastSensorData.latitude == 0.0 && lastSensorData.longitude == 0.0) {
    Serial.println("[PA] GPS bez fixa (0.0, 0.0) - wysyłam do aplikacji (pozycja oceanu)");
  }

  // Wyślij PA do aplikacji (używamy prędkości z wiadomości PA z Raspberry Pi)
  String response = "PA:";
  response += String(lastSensorData.longitude, 5);
  response += ":";
  response += String(lastSensorData.latitude, 5);
  response += ":";
  response += String(lastSensorData.speed_ms, 2);
  response += ":";
  response += String(lastSensorData.sequence);
  response += ":PA";
  tcp_send_line(response);
  
  Serial.print("[NADANO][TCP][PA] lon=");
  Serial.print(lastSensorData.longitude, 5);
  Serial.print(", lat=");
  Serial.print(lastSensorData.latitude, 5);
  Serial.print(", speed=");
  Serial.print(lastSensorData.speed_ms, 2);
  Serial.print(" m/s, seq=");
  Serial.println(lastSensorData.sequence);
}

// SI:mag:dep:SI – przekazujemy wiadomość SI z Raspberry Pi
void send_sensor_information_from_last() {
  if (!hasLastSensorData || lastSensorData.sensor_info.length() == 0) {
    return;
  }
  
  // Przekaż wiadomość SI bezpośrednio z Raspberry Pi
  tcp_send_line(lastSensorData.sensor_info);
  // Komunikat już jest w tcp_send_line()
}

// Parsowanie komend z aplikacji
void handle_tcp_command(const String& line) {
  if (line.startsWith("GBI")) {
    // Prośba o Boat Information
    Serial.println("[ODEBRANO][TCP][GBI] Żądanie informacji o łódce");
    send_boat_information();
    return;
  }

  // Protokół z prefiksem (np. SS:... , SA:..., SM:..., LI:...)
  int firstColon = line.indexOf(':');
  if (firstColon < 0) return;

  String cmd = line.substring(0, firstColon);

  if (cmd == "SS") {
    // Format: SS:left:right:winch:sNum:SS
    // Android wysyła left/right jako double (0.0–1.0), winch jako int (0-2)
    int p1 = firstColon + 1;
    int p2 = line.indexOf(':', p1);
    int p3 = p2 >= 0 ? line.indexOf(':', p2 + 1) : -1;
    int p4 = p3 >= 0 ? line.indexOf(':', p3 + 1) : -1;

    if (p2 < 0 || p3 < 0 || p4 < 0) return;

    String leftStr = line.substring(p1, p2);
    String rightStr = line.substring(p2 + 1, p3);
    String winchStr = line.substring(p3 + 1, p4);
    // sNum jest między p4 a kolejnym ':', ale tutaj go nie wykorzystujemy

    double left = leftStr.toDouble();
    double right = rightStr.toDouble();
    int winch = winchStr.toInt();

    // Mapowanie wartości z aplikacji mobilnej na format dla kontrolera (1-10)
    // Aplikacja mobilna konwertuje -80..80 na 1..10 przed wysłaniem,
    // ale obsługujemy też surowe wartości -80..80 dla kompatybilności wstecznej
    auto mapSpeed = [](double v) -> int {
      // Jeśli wartość jest już w zakresie 1-10, przekaż dalej
      if (v >= 1.0 && v <= 10.0) {
        return (int)round(v);
      }
      
      // Mapowanie surowych wartości -80..80 na 1..10 (dla kompatybilności wstecznej)
      // -80 -> 1 (reverse max), 0 -> 5 (neutral), 80 -> 10 (forward max)
      // Format dla ESC: 5 = neutral (stop), 1-4 = reverse, 6-10 = forward
      if (v == 0.0) {
        return 5; // Neutral (stop)
      }
      
      if (v < 0.0) {
        // Reverse: -80..-1 -> 1..4
        int mapped = (int)round(5.0 + (v / 80.0) * 4.0);
        if (mapped < 1) mapped = 1;
        if (mapped > 4) mapped = 4;
        return mapped;
      } else {
        // Forward: 1..80 -> 6..10
        int mapped = (int)round(5.0 + (v / 80.0) * 5.0);
        if (mapped < 6) mapped = 6;
        if (mapped > 10) mapped = 10;
        return mapped;
      }
    };

    motor_left_value = mapSpeed(left);
    motor_right_value = mapSpeed(right);
    
    // Aktualizuj stan zwijarki tylko jeśli się zmienił
    if (winch >= 0 && winch <= 2 && winch_state != winch) {
      winch_state = winch;
      Serial.print("[ODEBRANO][TCP][SS] winch state zmieniony na: ");
      Serial.print(winch_state);
      Serial.print(" (");
      if (winch_state == 0) Serial.print("UP");
      else if (winch_state == 1) Serial.print("STOP");
      else if (winch_state == 2) Serial.print("DOWN");
      Serial.println(")");
    }

    Serial.print("[ODEBRANO][TCP][SS] left=");
    Serial.print(motor_left_value);
    Serial.print(", right=");
    Serial.print(motor_right_value);
    Serial.print(", winch=");
    Serial.print(winch_state);
    Serial.println(" | Przekazuję przez LoRa...");
    
    // NATYCHMIAST wyślij przez LoRa (nie czekaj na loop())
    // To zapewnia, że komenda Stop i inne zmiany są natychmiast przekazywane
    sequence_counter++;
    String loraMessage = "SS:";
    loraMessage += String(motor_left_value);
    loraMessage += ":";
    loraMessage += String(motor_right_value);
    loraMessage += ":";
    loraMessage += String(winch_state);
    loraMessage += ":";
    loraMessage += String(sequence_counter);
    loraMessage += ":SS";
    
    LoRa.beginPacket();
    LoRa.print(loraMessage);
    LoRa.endPacket();
    
    packets_sent++;
    
    Serial.print("[NADANO][LORA][SS] left=");
    Serial.print(motor_left_value);
    Serial.print(", right=");
    Serial.print(motor_right_value);
    Serial.print(", winch=");
    Serial.print(winch_state);
    Serial.print(" (");
    if (winch_state == 0) Serial.print("UP");
    else if (winch_state == 1) Serial.print("STOP");
    else if (winch_state == 2) Serial.print("DOWN");
    Serial.print("), seq=");
    Serial.print(sequence_counter);
    Serial.print(" | Wysłano łącznie: ");
    Serial.println(packets_sent);
  } else if (cmd == "SA") {
    // Set Action – SA:action:payload:sNum:SA
    // UWAGA: Zwijarka jest teraz w SS, nie w SA
    Serial.print("[ODEBRANO][TCP][SA] ");
    Serial.print(line);
    Serial.println(" | Przekazuję przez LoRa...");
    
    LoRa.beginPacket();
    LoRa.print(line);
    LoRa.endPacket();
    
    Serial.print("[NADANO][LORA][SA] ");
    Serial.println(line);
    
  } else if (cmd == "SM") {
    // Set Mission – SM:mission:sNum:SM
    int p1 = firstColon + 1;
    int p2 = line.indexOf(':', p1);
    if (p2 < 0) return;
    String mission = line.substring(p1, p2);
    currentMission = mission;

    Serial.print("[ODEBRANO][TCP][SM] mission=");
    Serial.print(mission);
    Serial.println(" | Przekazuję przez LoRa...");
    
    // Przekaż przez LoRa do Raspberry Pi
    LoRa.beginPacket();
    LoRa.print(line);
    LoRa.endPacket();
    
    Serial.print("[NADANO][LORA][SM] ");
    Serial.println(line);
  } else if (cmd == "LI") {
    // Lost Information – wyślij ostatnie PA
    int p1 = firstColon + 1;
    int p2 = line.indexOf(':', p1);
    unsigned long seq = p2 > 0 ? line.substring(p1, p2).toInt() : 0;
    Serial.print("[ODEBRANO][TCP][LI] seq=");
    Serial.print(seq);
    Serial.println(" | Wysyłam ostatnie PA...");
    send_position_actualisation_from_last();
  } else {
    Serial.print("Nieznana komenda TCP: ");
    Serial.println(cmd);
  }
}

void send_motor_control() {
  // UWAGA: Gdy przychodzi SS przez TCP, od razu wysyłamy przez LoRa (w handle_tcp_command).
  // Ta funkcja jest wywoływana w loop() okresowo, ale teraz głównie dla kompatybilności.
  // Nie blokujemy wysyłania gdy wartości są na neutral, bo komenda Stop musi być przekazana.
  
  sequence_counter++;
  
  // Format: SS:left:right:winch:s_num:SS
  String message = "SS:";
  message += String(motor_left_value);
  message += ":";
  message += String(motor_right_value);
  message += ":";
  message += String(winch_state);
  message += ":";
  message += String(sequence_counter);
  message += ":SS";
  
  // Wyślij wiadomość tekstową przez LoRa
  LoRa.beginPacket();
  LoRa.print(message);
  LoRa.endPacket();
  
  packets_sent++;
  
  Serial.print("[NADANO][LORA][SS] left=");
  Serial.print(motor_left_value);
  Serial.print(", right=");
  Serial.print(motor_right_value);
  Serial.print(", winch=");
  Serial.print(winch_state);
  Serial.print(" (");
  if (winch_state == 0) Serial.print("UP");
  else if (winch_state == 1) Serial.print("STOP");
  else if (winch_state == 2) Serial.print("DOWN");
  Serial.print("), seq=");
  Serial.print(sequence_counter);
  Serial.print(" | Wysłano łącznie: ");
  Serial.println(packets_sent);
}

void receive_sensor_data() {
  // Sprawdź czy są dostępne dane
  int packetSize = LoRa.parsePacket();
  
  // Debug - sprawdź czy w ogóle próbujemy odbierać (co 5 sekund)
  static unsigned long last_check = 0;
  unsigned long now = millis();
  if (now - last_check > 5000) {
    Serial.print("[DEBUG][RX] Sprawdzam odbiór... parsePacket()=");
    Serial.print(packetSize);
    Serial.print(" | Odebrano łącznie: ");
    Serial.print(packets_received);
    Serial.print(" | Wysłano łącznie: ");
    Serial.println(packets_sent);
    last_check = now;
  }
  
  if (packetSize > 0) {
    // Odbierz wiadomość tekstową
    String message = "";
    while (LoRa.available()) {
      message += (char)LoRa.read();
    }
    message.trim();
    
    if (message.length() == 0) return;
    
    packets_received++;
    
    // Parsuj wiadomość
    int firstColon = message.indexOf(':');
    if (firstColon < 0) {
      Serial.print("[ODEBRANO][LORA][BŁĄD] Nieprawidłowy format (brak ':'): ");
      Serial.println(message);
      return;
    }
    
    String cmd = message.substring(0, firstColon);
    
    Serial.print("[ODEBRANO][LORA][");
    Serial.print(cmd);
    Serial.print("] ");
    Serial.print(message);
    Serial.print(" | RSSI: ");
    Serial.print(LoRa.packetRssi());
    Serial.println(" dBm");
    
    // PA - Position Actualisation
    if (cmd == "PA" && message.endsWith(":PA")) {
      // Format: PA:lon:lat:speed:s_num:PA
      int p1 = firstColon + 1;
      int p2 = message.indexOf(':', p1);
      int p3 = message.indexOf(':', p2 + 1);
      int p4 = message.indexOf(':', p3 + 1);
      
      if (p2 > 0 && p3 > 0 && p4 > 0) {
        float lon = message.substring(p1, p2).toFloat();
        float lat = message.substring(p2 + 1, p3).toFloat();
        float speed = message.substring(p3 + 1, p4).toFloat();
        unsigned long seq = message.substring(p4 + 1, message.length() - 3).toInt();
        
        hasLastSensorData = true;
        lastSensorData.longitude = lon;
        lastSensorData.latitude = lat;
        lastSensorData.speed_ms = speed;
        lastSensorData.sequence = seq;
        
        Serial.print("[ODEBRANO][LORA][PA] lon=");
        Serial.print(lon, 5);
        Serial.print(", lat=");
        Serial.print(lat, 5);
        Serial.print(", speed=");
        Serial.print(speed, 2);
        Serial.print(" m/s, seq=");
        Serial.println(seq);
        
        // Przekaż do aplikacji przez TCP
        if (tcpClientConnected && tcpClient.connected()) {
          send_position_actualisation_from_last();
        }
      }
    }
    // SI - Sensor Information
    else if (cmd == "SI" && message.endsWith(":SI")) {
      // Format: SI:mag:dep:SI
      // Przechowujemy całą wiadomość i przekazujemy do aplikacji
      hasLastSensorData = true;
      lastSensorData.sensor_info = message;
      
      // Wyświetl uproszczone informacje
      int p1 = firstColon + 1;
      int p2 = message.indexOf(':', p1);
      if (p2 > p1) {
        String magData = message.substring(p1, p2);
        Serial.print("[ODEBRANO][LORA][SI] mag=");
        Serial.print(magData.substring(0, min(30, int(magData.length()))));
        if (magData.length() > 30) Serial.print("...");
        Serial.println();
      } else {
        Serial.println("[ODEBRANO][LORA][SI] (pełne dane z czujników)");
      }
      
      // Przekaż do aplikacji przez TCP
      if (tcpClientConnected && tcpClient.connected()) {
        send_sensor_information_from_last();
      }
    }
    // BI - Boat Information
    else if (cmd == "BI" && message.endsWith(":BI")) {
      // Format: BI:name:captain:mission:BI
      int p1 = firstColon + 1;
      int p2 = message.indexOf(':', p1);
      int p3 = message.indexOf(':', p2 + 1);
      if (p2 > 0 && p3 > 0) {
        String name = message.substring(p1, p2);
        String captain = message.substring(p2 + 1, p3);
        String mission = message.substring(p3 + 1, message.length() - 3);
        Serial.print("[ODEBRANO][LORA][BI] name=");
        Serial.print(name);
        Serial.print(", captain=");
        Serial.print(captain);
        Serial.print(", mission=");
        Serial.println(mission);
      }
      // Przekaż do aplikacji przez TCP
      if (tcpClientConnected && tcpClient.connected()) {
        tcp_send_line(message);
      }
    }
    // BIC - Boat Information Change
    else if (cmd == "BIC" && message.endsWith(":BIC")) {
      // Format: BIC:name:captain:mission:BIC
      int p1 = firstColon + 1;
      int p2 = message.indexOf(':', p1);
      int p3 = message.indexOf(':', p2 + 1);
      if (p2 > 0 && p3 > 0) {
        String mission = message.substring(p3 + 1, message.length() - 4);
        Serial.print("[ODEBRANO][LORA][BIC] mission=");
        Serial.println(mission);
      }
      // Przekaż do aplikacji przez TCP
      if (tcpClientConnected && tcpClient.connected()) {
        tcp_send_line(message);
      }
    }
    // WI - Warning Information
    else if (cmd == "WI" && message.endsWith(":WI")) {
      // Format: WI:info_code:WI
      int p1 = firstColon + 1;
      int p2 = message.lastIndexOf(':');
      String infoCode = p2 > p1 ? message.substring(p1, p2) : "";
      Serial.print("[ODEBRANO][LORA][WI] info_code=");
      Serial.println(infoCode);
      // Przekaż do aplikacji przez TCP
      if (tcpClientConnected && tcpClient.connected()) {
        tcp_send_line(message);
      }
    }
    // LI - Lost Information
    else if (cmd == "LI" && message.endsWith(":LI")) {
      // Format: LI:s_num:LI
      int p1 = firstColon + 1;
      int p2 = message.lastIndexOf(':');
      unsigned long seq = p2 > p1 ? message.substring(p1, p2).toInt() : 0;
      Serial.print("[ODEBRANO][LORA][LI] seq=");
      Serial.println(seq);
      // Na żądanie LI z aplikacji, wyślemy ostatnie PA
      if (tcpClientConnected && tcpClient.connected()) {
        send_position_actualisation_from_last();
      }
    }
    else {
      Serial.print("[BŁĄD] Nieznana komenda: ");
      Serial.println(cmd);
    }
  }
}

