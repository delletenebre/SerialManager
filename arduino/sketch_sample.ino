int counter = 0;

void setup() {
  Serial.begin(9600);
}


void loop() {
  Serial.println("<testkey:" + String(counter) + ">"); // Эта строчка ключевая - в этом формате SerialManager принимает данные
  counter++;
  delay(3000);
}