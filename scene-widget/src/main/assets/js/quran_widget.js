/**
 * Quran Widget JS Logic
 * Selects a daily verse based on the current date.
 * Contains embedded verse data for offline support.
 * Uses generic widget module to update widget text fields.
 */

// Built-in verse collection (Arabic + English translation)
var verses = [
    {
        arabic: "\u0628\u0650\u0633\u0652\u0645\u0650 \u0627\u0644\u0644\u0651\u064e\u0647\u0650 \u0627\u0644\u0631\u0651\u064e\u062d\u0652\u0645\u064e\u0640\u0646\u0650 \u0627\u0644\u0631\u0651\u064e\u062d\u0650\u064a\u0645\u0650",
        translation: "In the name of Allah, the Most Gracious, the Most Merciful",
        surah: "Al-Fatiha",
        ayah: "1:1"
    },
    {
        arabic: "\u0627\u0644\u0652\u062d\u064e\u0645\u0652\u062f\u064f \u0644\u0650\u0644\u0651\u064e\u0647\u0650 \u0631\u064e\u0628\u0651\u0650 \u0627\u0644\u0652\u0639\u064e\u0627\u0644\u064e\u0645\u0650\u064a\u0646\u064e",
        translation: "All praise is due to Allah, Lord of the worlds",
        surah: "Al-Fatiha",
        ayah: "1:2"
    },
    {
        arabic: "\u0627\u0644\u0631\u0651\u064e\u062d\u0652\u0645\u064e\u0640\u0646\u0650 \u0627\u0644\u0631\u0651\u064e\u062d\u0650\u064a\u0645\u0650",
        translation: "The Most Gracious, the Most Merciful",
        surah: "Al-Fatiha",
        ayah: "1:3"
    },
    {
        arabic: "\u0625\u0650\u064a\u0651\u064e\u0627\u0643\u064e \u0646\u064e\u0639\u0652\u0628\u064f\u062f\u064f \u0648\u064e\u0625\u0650\u064a\u0651\u064e\u0627\u0643\u064e \u0646\u064e\u0633\u0652\u062a\u064e\u0639\u0650\u064a\u0646\u064f",
        translation: "It is You we worship and You we ask for help",
        surah: "Al-Fatiha",
        ayah: "1:5"
    },
    {
        arabic: "\u0630\u064e\u0640\u0644\u0650\u0643\u064e \u0627\u0644\u0652\u0643\u0650\u062a\u064e\u0627\u0628\u064f \u0644\u064e\u0627 \u0631\u064e\u064a\u0652\u0628\u064e \u0641\u0650\u064a\u0647\u0650 \u0647\u064f\u062f\u064b\u0649 \u0644\u0650\u0644\u0652\u0645\u064f\u062a\u0651\u064e\u0642\u0650\u064a\u0646\u064e",
        translation: "This is the Book about which there is no doubt, a guidance for those conscious of Allah",
        surah: "Al-Baqarah",
        ayah: "2:2"
    },
    {
        arabic: "\u0627\u0644\u0644\u0651\u064e\u0647\u064f \u0644\u064e\u0627 \u0625\u0650\u0644\u064e\u0640\u0647\u064e \u0625\u0650\u0644\u0651\u064e\u0627 \u0647\u064f\u0648\u064e \u0627\u0644\u0652\u062d\u064e\u064a\u0651\u064f \u0627\u0644\u0652\u0642\u064e\u064a\u0651\u064f\u0648\u0645\u064f",
        translation: "Allah - there is no deity except Him, the Ever-Living, the Sustainer of existence",
        surah: "Al-Baqarah",
        ayah: "2:255"
    },
    {
        arabic: "\u0631\u064e\u0628\u0651\u064e\u0646\u064e\u0627 \u0622\u062a\u0650\u0646\u064e\u0627 \u0641\u0650\u064a \u0627\u0644\u062f\u0651\u064f\u0646\u0652\u064a\u064e\u0627 \u062d\u064e\u0633\u064e\u0646\u064e\u0629\u064b \u0648\u064e\u0641\u0650\u064a \u0627\u0644\u0652\u0622\u062e\u0650\u0631\u064e\u0629\u0650 \u062d\u064e\u0633\u064e\u0646\u064e\u0629\u064b \u0648\u064e\u0642\u0650\u0646\u064e\u0627 \u0639\u064e\u0630\u064e\u0627\u0628\u064e \u0627\u0644\u0646\u0651\u064e\u0627\u0631\u0650",
        translation: "Our Lord, give us in this world good and in the Hereafter good and protect us from the punishment of the Fire",
        surah: "Al-Baqarah",
        ayah: "2:201"
    },
    {
        arabic: "\u0644\u064e\u0627 \u064a\u064f\u0643\u064e\u0644\u0651\u0650\u0641\u064f \u0627\u0644\u0644\u0651\u064e\u0647\u064f \u0646\u064e\u0641\u0652\u0633\u064b\u0627 \u0625\u0650\u0644\u0651\u064e\u0627 \u0648\u064f\u0633\u0652\u0639\u064e\u0647\u064e\u0627",
        translation: "Allah does not burden a soul beyond that it can bear",
        surah: "Al-Baqarah",
        ayah: "2:286"
    },
    {
        arabic: "\u0648\u064e\u0645\u064e\u0646 \u064a\u064e\u062a\u064e\u0648\u064e\u0643\u0651\u064e\u0644\u0652 \u0639\u064e\u0644\u064e\u0649 \u0627\u0644\u0644\u0651\u064e\u0647\u0650 \u0641\u064e\u0647\u064f\u0648\u064e \u062d\u064e\u0633\u0652\u0628\u064f\u0647\u064f",
        translation: "And whoever relies upon Allah - then He is sufficient for him",
        surah: "At-Talaq",
        ayah: "65:3"
    },
    {
        arabic: "\u0625\u0650\u0646\u0651\u064e \u0645\u064e\u0639\u064e \u0627\u0644\u0652\u0639\u064f\u0633\u0652\u0631\u0650 \u064a\u064f\u0633\u0652\u0631\u064b\u0627",
        translation: "Indeed, with hardship comes ease",
        surah: "Ash-Sharh",
        ayah: "94:6"
    },
    {
        arabic: "\u0641\u064e\u0627\u0630\u0652\u0643\u064f\u0631\u064f\u0648\u0646\u0650\u064a \u0623\u064e\u0630\u0652\u0643\u064f\u0631\u0652\u0643\u064f\u0645\u0652 \u0648\u064e\u0627\u0634\u0652\u0643\u064f\u0631\u064f\u0648\u0627 \u0644\u0650\u064a \u0648\u064e\u0644\u064e\u0627 \u062a\u064e\u0643\u0652\u0641\u064f\u0631\u064f\u0648\u0646\u0650",
        translation: "So remember Me; I will remember you. And be grateful to Me and do not deny Me",
        surah: "Al-Baqarah",
        ayah: "2:152"
    },
    {
        arabic: "\u0648\u064e\u0644\u064e\u0633\u064e\u0648\u0652\u0641\u064e \u064a\u064f\u0639\u0652\u0637\u0650\u064a\u0643\u064e \u0631\u064e\u0628\u0651\u064f\u0643\u064e \u0641\u064e\u062a\u064e\u0631\u0652\u0636\u064e\u0649\u0640",
        translation: "And your Lord is going to give you, and you will be satisfied",
        surah: "Ad-Duha",
        ayah: "93:5"
    },
    {
        arabic: "\u0642\u064f\u0644\u0652 \u0647\u064f\u0648\u064e \u0627\u0644\u0644\u0651\u064e\u0647\u064f \u0623\u064e\u062d\u064e\u062f\u064c",
        translation: "Say: He is Allah, the One",
        surah: "Al-Ikhlas",
        ayah: "112:1"
    },
    {
        arabic: "\u0648\u064e\u0625\u0650\u0630\u064e\u0627 \u0633\u064e\u0623\u064e\u0644\u064e\u0643\u064e \u0639\u0650\u0628\u064e\u0627\u062f\u0650\u064a \u0639\u064e\u0646\u0651\u0650\u064a \u0641\u064e\u0625\u0650\u0646\u0651\u0650\u064a \u0642\u064e\u0631\u0650\u064a\u0628\u064c",
        translation: "And when My servants ask you concerning Me - indeed I am near",
        surah: "Al-Baqarah",
        ayah: "2:186"
    },
    {
        arabic: "\u0625\u0650\u0646\u0651\u064e \u0627\u0644\u0644\u0651\u064e\u0647\u064e \u0645\u064e\u0639\u064e \u0627\u0644\u0635\u0651\u064e\u0627\u0628\u0650\u0631\u0650\u064a\u0646\u064e",
        translation: "Indeed, Allah is with the patient",
        surah: "Al-Baqarah",
        ayah: "2:153"
    },
    {
        arabic: "\u0631\u064e\u0628\u0651\u0650 \u0627\u0634\u0652\u0631\u064e\u062d\u0652 \u0644\u0650\u064a \u0635\u064e\u062f\u0652\u0631\u0650\u064a \u0648\u064e\u064a\u064e\u0633\u0651\u0650\u0631\u0652 \u0644\u0650\u064a \u0623\u064e\u0645\u0652\u0631\u0650\u064a",
        translation: "My Lord, expand for me my breast and ease for me my task",
        surah: "Ta-Ha",
        ayah: "20:25-26"
    },
    {
        arabic: "\u0648\u064e\u0646\u064f\u0646\u064e\u0632\u0651\u0650\u0644\u064f \u0645\u0650\u0646\u064e \u0627\u0644\u0652\u0642\u064f\u0631\u0652\u0622\u0646\u0650 \u0645\u064e\u0627 \u0647\u064f\u0648\u064e \u0634\u0650\u0641\u064e\u0627\u0621\u064c \u0648\u064e\u0631\u064e\u062d\u0652\u0645\u064e\u0629\u064c \u0644\u0650\u0644\u0652\u0645\u064f\u0624\u0652\u0645\u0650\u0646\u0650\u064a\u0646\u064e",
        translation: "And We send down of the Quran that which is healing and mercy for the believers",
        surah: "Al-Isra",
        ayah: "17:82"
    },
    {
        arabic: "\u0641\u064e\u0628\u0650\u0623\u064e\u064a\u0651\u0650 \u0622\u0644\u064e\u0627\u0621\u0650 \u0631\u064e\u0628\u0651\u0650\u0643\u064f\u0645\u064e\u0627 \u062a\u064f\u0643\u064e\u0630\u0651\u0650\u0628\u064e\u0627\u0646\u0650",
        translation: "So which of the favors of your Lord would you deny?",
        surah: "Ar-Rahman",
        ayah: "55:13"
    },
    {
        arabic: "\u0648\u064e\u0642\u064f\u0644 \u0631\u0651\u064e\u0628\u0651\u0650 \u0632\u0650\u062f\u0652\u0646\u0650\u064a \u0639\u0650\u0644\u0652\u0645\u064b\u0627",
        translation: "And say: My Lord, increase me in knowledge",
        surah: "Ta-Ha",
        ayah: "20:114"
    },
    {
        arabic: "\u062d\u064e\u0633\u0652\u0628\u064f\u0646\u064e\u0627 \u0627\u0644\u0644\u0651\u064e\u0647\u064f \u0648\u064e\u0646\u0650\u0639\u0652\u0645\u064e \u0627\u0644\u0652\u0648\u064e\u0643\u0650\u064a\u0644\u064f",
        translation: "Sufficient for us is Allah, and He is the best Disposer of affairs",
        surah: "Ali 'Imran",
        ayah: "3:173"
    },
    {
        arabic: "\u0648\u064e\u0644\u064e\u0627 \u062a\u064e\u064a\u0652\u0623\u064e\u0633\u064f\u0648\u0627 \u0645\u0650\u0646 \u0631\u0651\u064e\u0648\u0652\u062d\u0650 \u0627\u0644\u0644\u0651\u064e\u0647\u0650",
        translation: "And do not despair of relief from Allah",
        surah: "Yusuf",
        ayah: "12:87"
    },
    {
        arabic: "\u0623\u064e\u0644\u064e\u0627 \u0628\u0650\u0630\u0650\u0643\u0652\u0631\u0650 \u0627\u0644\u0644\u0651\u064e\u0647\u0650 \u062a\u064e\u0637\u0652\u0645\u064e\u0626\u0650\u0646\u0651\u064f \u0627\u0644\u0652\u0642\u064f\u0644\u064f\u0648\u0628\u064f",
        translation: "Verily, in the remembrance of Allah do hearts find rest",
        surah: "Ar-Ra'd",
        ayah: "13:28"
    },
    {
        arabic: "\u0631\u064e\u0628\u0651\u064e\u0646\u064e\u0627 \u0644\u064e\u0627 \u062a\u064f\u0632\u0650\u063a\u0652 \u0642\u064f\u0644\u064f\u0648\u0628\u064e\u0646\u064e\u0627 \u0628\u064e\u0639\u0652\u062f\u064e \u0625\u0650\u0630\u0652 \u0647\u064e\u062f\u064e\u064a\u0652\u062a\u064e\u0646\u064e\u0627 \u0648\u064e\u0647\u064e\u0628\u0652 \u0644\u064e\u0646\u064e\u0627 \u0645\u0650\u0646 \u0644\u0651\u064e\u062f\u064f\u0646\u0643\u064e \u0631\u064e\u062d\u0652\u0645\u064e\u0629\u064b",
        translation: "Our Lord, let not our hearts deviate after You have guided us and grant us from Yourself mercy",
        surah: "Ali 'Imran",
        ayah: "3:8"
    },
    {
        arabic: "\u0648\u064e\u0627\u0635\u0652\u0628\u0650\u0631\u0652 \u0641\u064e\u0625\u0650\u0646\u0651\u064e \u0627\u0644\u0644\u0651\u064e\u0647\u064e \u0644\u064e\u0627 \u064a\u064f\u0636\u0650\u064a\u0639\u064f \u0623\u064e\u062c\u0652\u0631\u064e \u0627\u0644\u0652\u0645\u064f\u062d\u0652\u0633\u0650\u0646\u0650\u064a\u0646\u064e",
        translation: "And be patient, for indeed, Allah does not allow to be lost the reward of those who do good",
        surah: "Hud",
        ayah: "11:115"
    },
    {
        arabic: "\u0625\u0650\u0646\u0651\u064e\u0627 \u0641\u064e\u062a\u064e\u062d\u0652\u0646\u064e\u0627 \u0644\u064e\u0643\u064e \u0641\u064e\u062a\u0652\u062d\u064b\u0627 \u0645\u0651\u064f\u0628\u0650\u064a\u0646\u064b\u0627",
        translation: "Indeed, We have given you a clear conquest",
        surah: "Al-Fath",
        ayah: "48:1"
    },
    {
        arabic: "\u064a\u064e\u0627 \u0623\u064e\u064a\u0651\u064f\u0647\u064e\u0627 \u0627\u0644\u0651\u064e\u0630\u0650\u064a\u0646\u064e \u0622\u0645\u064e\u0646\u064f\u0648\u0627 \u0627\u0633\u0652\u062a\u064e\u0639\u0650\u064a\u0646\u064f\u0648\u0627 \u0628\u0650\u0627\u0644\u0635\u0651\u064e\u0628\u0652\u0631\u0650 \u0648\u064e\u0627\u0644\u0635\u0651\u064e\u0644\u064e\u0627\u0629\u0650",
        translation: "O you who have believed, seek help through patience and prayer",
        surah: "Al-Baqarah",
        ayah: "2:153"
    },
    {
        arabic: "\u0648\u064e\u0627\u0644\u0644\u0651\u064e\u0647\u064f \u064a\u064f\u062d\u0650\u0628\u0651\u064f \u0627\u0644\u0635\u0651\u064e\u0627\u0628\u0650\u0631\u0650\u064a\u0646\u064e",
        translation: "And Allah loves the steadfast",
        surah: "Ali 'Imran",
        ayah: "3:146"
    },
    {
        arabic: "\u0648\u064e\u0644\u064e\u0646\u064e\u0628\u0652\u0644\u064f\u0648\u064e\u0646\u0651\u064e\u0643\u064f\u0645 \u0628\u0650\u0634\u064e\u064a\u0652\u0621\u064d \u0645\u0651\u0650\u0646\u064e \u0627\u0644\u0652\u062e\u064e\u0648\u0652\u0641\u0650 \u0648\u064e\u0627\u0644\u0652\u062c\u064f\u0648\u0639\u0650",
        translation: "And We will surely test you with something of fear and hunger",
        surah: "Al-Baqarah",
        ayah: "2:155"
    },
    {
        arabic: "\u0631\u064e\u0628\u0651\u064e\u0646\u064e\u0627 \u062a\u064e\u0642\u064e\u0628\u0651\u064e\u0644\u0652 \u0645\u0650\u0646\u0651\u064e\u0627 \u0625\u0650\u0646\u0651\u064e\u0643\u064e \u0623\u064e\u0646\u062a\u064e \u0627\u0644\u0633\u0651\u064e\u0645\u0650\u064a\u0639\u064f \u0627\u0644\u0652\u0639\u064e\u0644\u0650\u064a\u0645\u064f",
        translation: "Our Lord, accept from us. Indeed You are the Hearing, the Knowing",
        surah: "Al-Baqarah",
        ayah: "2:127"
    },
    {
        arabic: "\u0648\u064e\u0645\u064e\u0627 \u062a\u064e\u0648\u0652\u0641\u0650\u064a\u0642\u0650\u064a \u0625\u0650\u0644\u0651\u064e\u0627 \u0628\u0650\u0627\u0644\u0644\u0651\u064e\u0647\u0650 \u0639\u064e\u0644\u064e\u064a\u0652\u0647\u0650 \u062a\u064e\u0648\u064e\u0643\u0651\u064e\u0644\u0652\u062a\u064f \u0648\u064e\u0625\u0650\u0644\u064e\u064a\u0652\u0647\u0650 \u0623\u064f\u0646\u0650\u064a\u0628\u064f",
        translation: "And my success is not but through Allah. Upon Him I have relied, and to Him I return",
        surah: "Hud",
        ayah: "11:88"
    }
];

/**
 * Get today's verse based on the day of year (internal helper).
 * Returns an object (not JSON string).
 */
function getDailyVerse_internal() {
    var now = new Date();
    var start = new Date(now.getFullYear(), 0, 0);
    var diff = now - start;
    var oneDay = 1000 * 60 * 60 * 24;
    var dayOfYear = Math.floor(diff / oneDay);

    var index = dayOfYear % verses.length;
    var verse = verses[index];

    // Format date string
    var months = ['January', 'February', 'March', 'April', 'May', 'June',
                  'July', 'August', 'September', 'October', 'November', 'December'];
    var dateStr = months[now.getMonth()] + ' ' + now.getDate() + ', ' + now.getFullYear();

    return {
        arabic: verse.arabic,
        translation: verse.translation,
        surah: verse.surah,
        ayah: verse.ayah,
        reference: verse.surah + ' ' + verse.ayah,
        date: dateStr
    };
}

// Execute: update widget and return dialog result
(function() {
    var verse = getDailyVerse_internal();

    // Update widget text fields (may silently fail if no widget exists)
    try {
        __bridge.invoke('widget', 'updateText', JSON.stringify({
            text_line_1: verse.arabic,
            text_line_2: verse.translation,
            text_line_3: verse.reference,
            text_line_4: verse.date
        }));
    } catch(e) {
        // Widget module may not be registered — ignore
    }

    // Return formatted result for dialog display
    return JSON.stringify({
        title: '\u4eca\u65e5\u7ecf\u6587',
        message: verse.arabic + '\n\n' + verse.translation + '\n\n' + verse.reference + ' | ' + verse.date
    });
})();
