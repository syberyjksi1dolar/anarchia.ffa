# Anarchia Rangi

Osobny mod Fabric dla Minecraft Java 26.1.1.

## Funkcje
- Zakładka kreatywna „Księgi”.
- Przedmioty rang: VIP, SVIP, MVIP, GVIP, DVIP i TWÓRCA.
- PPM z przedmiotem rangi aktywuje rangę.
- Ranga jest zapisywana w scoreboardzie świata, więc pozostaje po ponownym wejściu.
- Prefiks i kolor nicku są ustawiane przez drużynę scoreboardu.

## Kolory
- VIP — żółty
- SVIP — zielony
- MVIP — jasnozielony prefiks (RGB); kolor nicku korzysta z najbliższego standardowego koloru drużyny
- GVIP — niebieski
- DVIP — ciemnozielony
- TWÓRCA — fioletowy

## Budowanie
Wymagane Java 25 i dostęp do internetu przy pierwszym buildzie.

```bash
gradle build
```

Gotowy plik będzie w `build/libs/`.

## Ważne uwagi
- To osobny mod — nie zawiera pozostałych elementów Anarchia.ffa.
- Przedmiot rangi w zakładce Księgi jest przykładowym przedmiotem do testowania. W docelowym serwerze trzeba go wydawać dopiero po spełnieniu warunków zdobycia rangi.
- Minecraft pozwala graczowi należeć tylko do jednej drużyny scoreboardu. Aktywowanie rangi może więc kolidować z innym pluginem/modem, który używa drużyn do prefixów.
- MVIP jest opisany jako jasnozielony, ale standardowy ChatFormatting nie ma osobnego jasnozielonego koloru; w tej wersji jest użyty zielony.
