package com.example.mymod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.component.ItemLore;

/**
 * Opisy przedmiotów widoczne po najechaniu kursorem (w ekwipunku, w zakładkach i w skrzyniach):
 * co robi przedmiot, jak go użyć i ile wynosi cooldown. Każdy opis to {co robi, jak użyć, uwagi}.
 * Opis dokładamy w ModItems.register jako domyślny składnik LORE przedmiotu.
 */
public final class Opisy {
	private static final int WIDTH = 40;
	private static final Map<String, String[]> OPISY = new HashMap<>();

	private Opisy() {
	}

	private static void put(String id, String co, String uzycie, String uwagi) {
		OPISY.put(id, new String[] { co, uzycie, uwagi });
	}

	/** Opis przedmiotu o danym id (np. "kosa") albo null, gdy nie ma. */
	public static ItemLore lore(String id) {
		String[] o = OPISY.get(id);
		if (o == null) {
			o = fallback(id);
		}
		if (o == null) {
			return null;
		}
		List<Component> lines = new ArrayList<>();
		wrap(lines, "Co robi: ", ChatFormatting.GOLD, o[0]);
		wrap(lines, "Użycie: ", ChatFormatting.GREEN, o[1]);
		if (o[2] != null && !o[2].isEmpty()) {
			wrap(lines, "Uwagi: ", ChatFormatting.AQUA, o[2]);
		}
		return new ItemLore(lines);
	}

	private static void wrap(List<Component> out, String label, ChatFormatting color, String text) {
		String[] words = text.split(" ");
		StringBuilder cur = new StringBuilder();
		boolean first = true;
		for (String w : words) {
			if (cur.length() > 0 && cur.length() + 1 + w.length() > WIDTH) {
				out.add(line(first ? label : "", color, cur.toString()));
				first = false;
				cur.setLength(0);
			}
			if (cur.length() > 0) {
				cur.append(' ');
			}
			cur.append(w);
		}
		if (cur.length() > 0) {
			out.add(line(first ? label : "", color, cur.toString()));
		}
	}

	private static Component line(String label, ChatFormatting color, String text) {
		MutableComponent c = Component.literal(label).withStyle(color).withStyle(s -> s.withItalic(false));
		c.append(Component.literal(text).withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false)));
		return c;
	}

	/** Opisy zbiorcze: kostiumy (4 części), skrzynie, klucze, księgi, rangi. */
	private static String[] fallback(String id) {
		if (id.startsWith("skrzynia_")) {
			return new String[] { "Skrzynia z losową nagrodą. Nagrody możesz obejrzeć w podglądzie.",
					"Postaw skrzynię i kliknij ją prawym przyciskiem. Do otwarcia potrzebny jest klucz tego samego rodzaju.",
					"Skrzyni nie da się zniszczyć. Klucze wypadają z zabitych bytów." };
		}
		if (id.startsWith("klucz_")) {
			return new String[] { "Klucz do skrzyni tego samego rodzaju.",
					"Kliknij prawym przyciskiem skrzynię i wybierz otwarcie. Klucz zużywa się przy otwarciu.",
					"Wypada z zabitych bytów, a rangi dają je codziennie (/klucze)." };
		}
		if (id.startsWith("ranga_")) {
			String r = id.substring(6).toUpperCase(java.util.Locale.ROOT);
			return new String[] { "Nadaje ci rangę " + r + ". Na środku ekranu wszystkich graczy pojawi się napis, że ją odebrałeś.",
					"Prawy klik.", "Przedmiot się zużywa. Nie da się odebrać tej samej rangi dwa razy." };
		}
		return null;
	}

	private static void kostium(String prefix, String nazwa, String efekty, String uzycie) {
		String czesc = "Część kostiumu: " + nazwa + ". Kostium to jeden przedmiot: załóż KLATĘ, a głowa, spodnie i buty ubiorą się same.";
		put(prefix + "_klata", "Kostium: " + nazwa + ". " + efekty, uzycie,
				"Załóż na klatę. Reszta ubiera się sama. Niezniszczalny.");
		put(prefix + "_helm", czesc, "Załóż klatę kostiumu.", "Niezniszczalny.");
		put(prefix + "_spodnie", czesc, "Załóż klatę kostiumu.", "Niezniszczalny.");
		put(prefix + "_buty", czesc, "Załóż klatę kostiumu.", "Niezniszczalny.");
	}

	private static void ksiega(String id, String nazwa, String co, String naCo) {
		put("ksiega_" + id, "Księga zaklęcia " + nazwa + ". " + co,
				"Weź księgę w jedną rękę, a " + naCo + " w drugą i kliknij prawym przyciskiem.",
				"Zaklęcie zapisuje się w opisie przedmiotu. Księgi dają się zdobyć z zabitych bytów.");
	}

	static {
		// ===== Eventówki =====
		put("chydro_klatka", "Strzela kulą ognistą (bez wybuchu). Tam, gdzie trafi, powstaje klatka z koralowca 6 x 9 na 15 s.",
				"Prawy klik.", "Nieskończony. Cooldown 1 min. W środku niszczyć możesz tylko ty.");
		put("siekiera_grincha", "Po uderzeniu zabiera 30% maksymalnego HP trafionego i uderza w niego piorun.",
				"Uderz przeciwnika.", "Cooldown 50 s (w tym czasie bije normalnie).");
		put("wodny_trojzab", "Prawy klik: torpeda jak Riptide, ale bez wody. Shift + prawy klik: wystrzał trójzębu, który po trafieniu woła piorun, zadaje 10 HP i podpala wszystko w promieniu 5 bloków.",
				"Prawy klik albo Shift + prawy klik.", "Nieskończony. Cooldown: torpeda 2 s, wystrzał 2 min.");
		put("zatopiony_statek", "Przez 10 s wszystkie byty w promieniu 14 bloków (poza tobą) dostają Powolne opadanie II, Zatrucie II i Osłabienie II. Ty dostajesz Siłę I.",
				"Postaw blok.", "Cooldown stawiania 1 min 30 s. Po restarcie serwera statek przestaje działać.");
		put("platek_sniegu", "Uderzony byt zamarza na 3 s (nie rusza się ani nie skacze).",
				"Uderz przeciwnika.", "Cooldown 10 s.");
		put("czerwony_balon", "Unosi cię w górę na 10 s (Lewitacja II), a potem opadasz miękko (nie giniesz od upadku).",
				"Prawy klik.", "Cooldown 30 s.");
		put("parasol", "Odpycha wszystkie byty w promieniu 6 bloków (poza tobą).",
				"Prawy klik.", "Cooldown 10 s.");
		put("serce", "Uderzony byt dostaje Spowolnienie i Ślepotę na 6 s.",
				"Uderz przeciwnika.", "Cooldown 10 s.");
		put("excalibur", "Miecz z Ostrością 7 (+4 obrażeń) i dodatkowymi +58% obrażeń.",
				"Trzymaj w ręce i walcz.", "Można go ulepszyć przedmiotem Zmianki.");
		put("totem_ulaskawienia", "Gdy umierasz, nie tracisz przedmiotów (wracają po odrodzeniu). Totem się zużywa.",
				"Trzymaj w lewej ręce.", "Giniesz normalnie, tylko przedmioty zostają.");
		put("rozdzka_iluzjonisty", "Lewy klik: 7 par szczęk Evokera przed tobą (każda 5 serc). Prawy klik: niewidzialność 5 s, klon biegnący prosto 5 s, a przez 30 s +2 serca i Szybkość II.",
				"Lewy albo prawy klik.", "Cooldown: lewy 10 s, prawy 40 s.");
		put("blok_widmo", "Po postawieniu od razu znika i zadaje 10 HP bytom w promieniu 5 bloków.",
				"Postaw blok.", "Cooldown 2 min.");
		put("wzmocniona_elytra", "Elytra z licznikiem 0-100%. Im dłużej lecisz, tym szybciej się ładuje. Przy 100% wlecenie w byta zadaje mu 10 HP.",
				"Załóż na klatę i leć.", "Licznik zeruje się po lądowaniu.");
		put("sakiewka", "Ma 54 miejsca. Po zabiciu bytu jego przedmioty trafiają do sakiewki (wystarczy mieć ją w ekwipunku).",
				"Prawy klik otwiera. Shift + prawy klik wysypuje wszystko na ziemię.", "");
		put("krew_wampira", "Leczy do pełna.",
				"Prawy klik (tylko gdy nie masz pełnego HP).", "Zużywalna. Cooldown 10 s.");
		put("cudowna_latarnia", "Po postawieniu dostajesz Siłę II (10 s), Odporność II (20 s) i Regenerację I (20 s).",
				"Postaw blok.", "Latarnia znika po 20 s.");
		put("bomba_maksima", "Wysadza obszar 5 x 5 i 3 bloki w dół od klikniętego bloku.",
				"Prawy klik w blok.", "Zużywalna, bez cooldownu.");
		put("wampirze_jablko", "Siła II na 2 s i 5 dodatkowych serc (żółtych) do 1 minuty.",
				"Zjedz.", "");
		put("wedka_nielota", "Wędka bez wytrzymałości. Złapany byt nie może latać ani używać elytry, dopóki jest na haczyku.",
				"Zarzuć wędkę na byt.", "Zakaz trwa jeszcze 5 s po uwolnieniu.");
		put("jajko_zmutowanego_creepera", "Przywołuje creepera, który nie niszczy bloków, a każdemu w pobliżu zadaje 50% jego maksymalnego HP.",
				"Prawy klik.", "Zużywalne.");
		put("rozga", "Trafiony byt dostaje Odrzut V (mocno odlatuje), a ty Powolne opadanie na 5 s.",
				"Uderz przeciwnika.", "Bez cooldownu.");
		put("smoczy_miecz", "Miecz z Ostrością V i Aspektem ognia II. Wystrzeliwuje perłę Endermana.",
				"Prawy klik wystrzeliwuje perłę. Uderzaj normalnie.", "Niezniszczalny. Cooldown 1 min.");
		put("rog_jednorozca", "Przywołuje jednorożca (konia), na którym od razu siadasz na 10 s. Wjechanie w blok rozwala obszar 4 x 4 x 4.",
				"Prawy klik.", "Cooldown 60 s.");
		put("kosa", "Zamyka ciebie i byt przed tobą w klatce 6 x 6 x 7 ze szkła na 15 s. Ty dostajesz Pośpiech V i Siłę I, zamknięty Ślepotę i Nudności.",
				"Prawy klik.", "Cooldown 60 s. W klatce niszczyć można tylko bloki postawione w środku.");
		put("kostka_rubika", "Miesza losowo przedmioty w ekwipunku trafionego gracza (nie w twoim).",
				"Prawy klik na gracza.", "Cooldown 3 s.");
		put("roza_kupidyna", "Daje Regenerację II, Odporność I i dodatkowe serca.",
				"Trzymaj w drugiej ręce.", "Bez cooldownu.");
		put("cieple_mleko", "Zdejmuje wszystkie negatywne efekty.",
				"Prawy klik.", "Cooldown 5 s.");
		put("lizak", "Zmniejsza cię do około 1 bloka wysokości i daje Siłę I.",
				"Trzymaj w drugiej ręce.", "");
		put("piekielny_miecz", "Miecz z Ostrością V. Podpala trafionego na 8 s (także byty odporne na ogień).",
				"Uderz przeciwnika.", "Niezniszczalny.");
		put("wata_cukrowa", "Naprawia losowy zniszczony element noszonej zbroi.",
				"Prawy klik.", "Zużywalna, bez cooldownu.");
		put("kroliczy_miecz", "Miecz z Ostrością V i podpaleniem na 4 s. Trafiony nie może skakać przez 6 s.",
				"Uderz przeciwnika.", "Niezniszczalny. Cooldown efektu 60 s.");
		put("turbo_trap", "Wykopuje dół 3 x 1 x 10 (szerokość, głębokość, wysokość) z ostrym naciekiem na dnie.",
				"Prawy klik w blok.", "Cooldown 60 s.");
		put("lopata_grincha", "Trafiony zamarza na 5 s. Jeśli to gracz, jego kamera obraca się losowo.",
				"Uderz przeciwnika.", "Niezniszczalna. Cooldown 30 s.");
		put("wedka_gwardiana", "Zamiast haczyka wystrzeliwuje Gwardiana na 6 bloków. Trafiony dostaje Powolne Wydobywanie III na 5 s, a Gwardian znika po 5 s.",
				"Prawy klik.", "Bez cooldownu.");
		put("zatruty_olowek", "Trafiony dostaje Zatrucie, Osłabienie II i Nudności na 20 s.",
				"Uderz przeciwnika.", "Niezniszczalny. Cooldown 120 s.");
		put("arcus_magnus", "Łuk z nieskończonymi strzałami. Trafienia pod rząd zadają 1, 2, 4, 8, 16, 32, 64 obrażeń (potem stale 64). Pudło resetuje serię.",
				"Prawy klik.", "Na ekranie widać bieżące obrażenia i w nawiasie następne.");
		put("marchewkowy_miecz", "Trafiony zamarza na 3 s.",
				"Uderz przeciwnika.", "Niezniszczalny. Cooldown 30 s.");
		put("marchewkowa_kusza", "Trafiony strzałą byt zostaje przyciągnięty do ciebie.",
				"Strzelaj jak z kuszy.", "Niezniszczalna.");
		put("balonik_z_helem", "Niszczy bloki nad tobą (3 x 3, do 10 bloków w górę) i unosi cię.",
				"Prawy klik.", "Cooldown 40 s.");
		put("piernik", "Daje Pośpiech X na 10 s.",
				"Prawy klik.", "Cooldown 2 min.");
		put("zatruty_trunek", "Rzucany jak mikstura. W miejscu upadku powstaje obszar 7 x 8 na 15 s: byty co sekundę tracą 2 HP i dostają Nudności na 20 s.",
				"Prawy klik.", "Cooldown 70 s.");
		put("rozgotowana_kukurydza", "Rzucana jak mikstura. W miejscu upadku powstaje obszar 7 x 8 na 15 s: byty co sekundę tracą 2 HP i dostają Nudności na 20 s.",
				"Prawy klik.", "Cooldown 70 s.");
		put("splesniala_kanapka", "Trafiony dostaje Nudności, Osłabienie i Spowolnienie na 30 s oraz obrażenia co 2 s.",
				"Prawy klik na byta.", "Cooldown 10 s między użyciami, a po 3 użyciach 120 s.");
		put("anarchiczny_kilof", "Kilof z Wydajnością 13 i Fortuną 4 (nakładane automatycznie).",
				"Kop jak zwykłym kilofem.", "Niezniszczalny.");
		put("luk_kupidyna", "Strzały mają 25% szans, by oślepić trafionego na 10 s.",
				"Strzelaj jak z łuku.", "Niezniszczalny.");
		put("anarchia_korona", "Siła III, Odporność IV, Szczęście II, Odporność na ogień i Szybkość IV.",
				"Załóż na głowę.", "Niezniszczalna.");
		put("slimowy_wiosna", "Wyrzuca cię w stronę patrzenia: 50% mocy to 4 bloki, 100% to 6 bloków. Przy pełnym naładowaniu trafione byty dostają 5 serc obrażeń.",
				"Kucaj z przedmiotem w ręce (1,25 s ładowania), a potem prawy klik.", "");
		put("kulka_balwana", "Trafiony byt dostaje Spowolnienie I i Osłabienie I na 3 s.",
				"Rzuć jak śnieżkę.", "Bez cooldownu.");
		put("wedka_18", "Zwykła wędka. Zwinięcie linki przyciąga cię do haczyka (do bloku albo bytu). Haczyk w graczu zadaje mu 2 serca.",
				"Zarzuć wędkę, a potem kliknij prawym przyciskiem, żeby zwinąć linkę.", "Cooldown 10 s tylko na atak graczy.");
		put("fajerwerka_wyrzutni", "Wystrzeliwuje cię w górę (do 8 bloków lotu). Pasek pokazuje paliwo.",
				"Trzymaj w ręce i przytrzymaj spację.", "Paliwo odnawia się po wylądowaniu.");
		put("rozdzka_magika", "Ma 6 segmentów, każdy ładuje się za 4 HP zadanych obrażeń. Po pełnym naładowaniu włącza niebieską wiązkę (5 s), która rani trafiony byt.",
				"Atakuj, żeby naładować, a potem przytrzymaj prawy przycisk.", "Po wyczerpaniu 5 s pasek zeruje się.");
		put("perelka_zamianka", "Po trafieniu byta zamienia miejscami ciebie i trafionego.",
				"Rzuć jak perłę Endermana.", "Cooldown 20 s.");
		put("wedeczka_perelka", "Złapany byt nie może się teleportować ani używać pereł Endu, dopóki jest na lince.",
				"Zarzuć wędkę na byt.", "");
		put("wilk", "Przywołuje oswojonego wilka, który atakuje najbliższego byta (nie ciebie).",
				"Prawy klik.", "Jeśli wilk zginie, jajko przepada. Jeśli zabije byta, jajko wraca.");
		put("dzialko", "Co sekundę strzela strzałą (1 HP) w najbliższego byta w zasięgu.",
				"Postaw blok.", "Twardy jak obsydian. Właściciel jest zapisany w świecie.");
		put("shulek_boxek", "Ma 27 miejsc. Prawy klik na bycie atakuje go po kolei wszystkimi broniami ze środka (obrażenia i zaklęcia się sumują).",
				"Prawy klik otwiera ekwipunek. Prawy klik na bycie atakuje.", "Cooldown ataku 5 min. Otwieranie go nie uruchamia.");
		put("dynamit", "Niszczy dokładnie jeden blok bedrocka.",
				"Prawy klik w bedrock.", "Zużywalny, bez cooldownu.");
		put("boski_topor", "Odrzuca byty w promieniu 15 bloków i daje ci 10 s niewrażliwości na obrażenia.",
				"Prawy klik.", "Cooldown 120 s.");
		put("rog_koozy", "Byty w promieniu 10 bloków dostają 6 HP obrażeń i lecą 10 bloków od ciebie.",
				"Prawy klik.", "Cooldown 60 s.");

		// ===== Pety =====
		put("pet_drakula", "Mała głowa Drakuli na ramieniu. Daje stałą Szybkość II, +2 serca, 10% kradzieży życia i +20% wytrzymałości zbroi.",
				"Prawy klik przywołuje i chowa peta.", "Możesz mieć tylko jednego peta naraz.");
		put("pet_balwanek", "Mała głowa bałwanka na ramieniu. Daje Pośpiech II, +2 serca i leczy 0,5 HP za każde uderzenie.",
				"Prawy klik przywołuje i chowa peta.", "Możesz mieć tylko jednego peta naraz.");

		// ===== Serce Życia i Zmianki =====
		put("serce_zycia", "Dodaje 1 serce na stałe (bazowo masz 10, maksimum to 30).",
				"Prawy klik.", "Po śmierci tracisz 1 dodane serce, ale nigdy nie spadasz poniżej 10. Wypada z graczy (10%).");
		put("zmianki", "Losuje dodatkowe procenty obrażeń miecza od -10% do 60%. Im wyższy wynik, tym rzadszy.",
				"Prawy klik, a potem wybierz miecz z listy.", "Procenty widać w opisie miecza. Wypadają z graczy (5%).");

		// ===== Kostiumy =====
		kostium("silacz", "Siłacz", "Siła I, +3 serca. 3x Shift: Siła III na 30 s.", "3x Shift (cooldown 2 min).");
		kostium("dolar", "Dolar", "+6 serc, +2 HP obrażeń, 7% mniej otrzymywanych obrażeń. 3x Shift: przez 30 s zostawiasz ślad śniegu 3 x 3, kto w niego wejdzie, dostaje Słabość II i Spowolnienie II, a ty Szybkość II.", "3x Shift (cooldown 2:30).");
		kostium("walentynki", "Walentynkowy", "+5 serc, +12% obrażeń, +5% obrony, 100% odporności na upadek.", "Działa cały czas po założeniu.");
		kostium("mim", "Mim", "+4 serca, +11% obrażeń. 3x Shift: Lustro bólu, przez 10 s obrażenia, które dostajesz, trafiają we wroga.", "3x Shift (cooldown 2 min).");
		kostium("pirat", "Pirat", "+13% obrażeń, ale 5% mniej obrony. Po zabiciu gracza leczysz 5 serc, innego bytu 2 serca.", "Działa cały czas po założeniu.");
		kostium("przeciwzakazeniowy", "Przeciwzakażeniowy", "Odporność na negatywne efekty, +3 serca, +10% obrony, +20% obrażeń dla przeciwnika (zarażenie).", "Działa cały czas po założeniu.");
		kostium("krolik", "Zabójczy Królik", "100% odporności na upadek, Szybkie Kopanie II, po pęknięciu totemu Siła II na 30 s oraz podwójny skok.", "Podwójny skok: skocz, puść spację w powietrzu i skocz drugi raz (co 15 s).");
		kostium("urwis", "Mały Urwis", "Szybkość II, 50% odporności na upadek, +10% obrażeń.", "Działa cały czas po założeniu.");
		kostium("nurek", "Nurek Głębinowy", "+4 serca, Szybkość II, +30% obrażeń w wodzie. Po wejściu do wody dostajesz Grację Delfina na 10 s.", "Gracja Delfina nie częściej niż co 30 s.");
		kostium("wampir", "Wampir", "Pocałunek wampira leczy za 7% zadanych obrażeń, +4 serca, +10% obrażeń.", "Działa cały czas po założeniu.");
		kostium("kgrinch", "Grinch", "+3 serca, +10% obrażeń. 3x Shift: Wybuch Grincha, przez 10 s nie da się na ciebie nałożyć eventówek (Kostka Rubika, klatka Kosy, negatywne efekty).", "3x Shift (cooldown 60 s).");

		// ===== Zbroje =====
		for (String s : new String[] { "helm", "klata", "spodnie", "buty" }) {
			put("zbroja_dolara_" + s, "Netherytowa zbroja z automatycznymi zaklęciami: Ochrona 10 i Niezniszczalność 10.",
					"Załóż na odpowiednią część ciała.", "Zaklęcia nakładają się same, gdy masz element w ekwipunku.");
			put("anarchiczna_zbroja_2_" + s, "Netherytowa zbroja z automatycznymi zaklęciami: Ochrona 7 i Niezniszczalność 7.",
					"Załóż na odpowiednią część ciała.", "Zaklęcia nakładają się same, gdy masz element w ekwipunku.");
		}

		// ===== Księgi =====
		ksiega("regeneracji", "Regeneracja", "Daje 2 razy szybszą regenerację serc.", "klatę (zbroję na tors)");
		ksiega("krytyczne", "Obrażenia Krytyczne", "25% szans, że trafienie krytyczne zadaje podwójne obrażenia.", "miecz");
		ksiega("ulaskawienia", "Ułaskawienie", "Przedmiot z zaklęciem nie wypada po śmierci i wraca po odrodzeniu.", "dowolny przedmiot");
		ksiega("wampiryzmu", "Wampiryzm", "Leczy 20% zadanych obrażeń.", "miecz");
		ksiega("wytrzymalosci", "Wytrzymałość", "Daje 40% większą wytrzymałość.", "zbroję albo narzędzie");
		ksiega("szczescia", "Szczęście", "Podwójny drop z bytu zabitego mieczem lub narzędziem z zaklęciem.", "miecz albo narzędzie");
		ksiega("szybkosci", "Szybkość", "Daje Szybkość II.", "buty");
		ksiega("wolnosci", "Wolność", "10% mniej obrażeń za każdą część zbroi z zaklęciem (maksimum 40%).", "część zbroi");
		ksiega("spadania", "Spadanie", "70% mniej obrażeń od upadku.", "spodnie");
		ksiega("niepodpalenia", "Niepodpalenie", "Daje Odporność na ogień i gasi płomienie na tobie.", "hełm");
		ksiega("mima", "Moc Mima", "Poniżej 4 serc jesteś nieśmiertelny przez 5 s.", "część zbroi");
		ksiega("zamrozenia", "Zamrożenie", "5% szans, że atakujący zamarznie na 4 s, gdy trzymasz miecz.", "miecz");
		ksiega("kzm", "Kradzież Życia Maksymalnego", "Po zabiciu bytu leczysz 1 serce.", "miecz");
		ksiega("piorunow", "Pioruny", "3% szans, że w atakującego uderzy piorun (90% jego HP, ogień w promieniu 5 bloków i zmutowany creeper), gdy trzymasz miecz.", "miecz");
		ksiega("ciernie", "Ciernie", "15% otrzymanych obrażeń wraca do atakującego.", "część zbroi");
		ksiega("serc", "Serca", "Daje +4 serca łącznie, jeśli którakolwiek część zbroi ma zaklęcie.", "część zbroi");
		ksiega("odbicia", "Odbicie", "5% szans na zablokowanie całego ciosu.", "część zbroi");
		ksiega("niewidzialnosci", "Niewidzialność", "Poniżej 4 serc: niewidzialność 5 s, Szybkość V, Regeneracja III, moby tracą cel.", "część zbroi");
		ksiega("oddychania", "Oddychanie", "Pozwala oddychać pod wodą.", "hełm");
		ksiega("autowytapiania", "Auto-Wytapianie", "Wydobyte rudy od razu się przetapiają.", "narzędzie (kilof, siekierę, łopatę)");
		ksiega("magnesu", "Magnes", "Wydobyte przedmioty trafiają prosto do ekwipunku.", "narzędzie (kilof, siekierę, łopatę)");
		ksiega("doswiadczenia", "Doświadczenie", "+2 XP za każdy wykopany blok i +5 XP za zabitego bytu.", "narzędzie (kilof, siekierę, łopatę)");
	}
}
