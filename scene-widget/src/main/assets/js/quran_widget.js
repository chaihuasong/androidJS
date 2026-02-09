/**
 * Quran Widget JS Logic
 * Selects a daily verse based on the current date.
 * Contains embedded verse data for offline support.
 */

// Built-in verse collection (Arabic + English translation)
var verses = [
    {
        arabic: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
        translation: "In the name of Allah, the Most Gracious, the Most Merciful",
        surah: "Al-Fatiha",
        ayah: "1:1"
    },
    {
        arabic: "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
        translation: "All praise is due to Allah, Lord of the worlds",
        surah: "Al-Fatiha",
        ayah: "1:2"
    },
    {
        arabic: "الرَّحْمَٰنِ الرَّحِيمِ",
        translation: "The Most Gracious, the Most Merciful",
        surah: "Al-Fatiha",
        ayah: "1:3"
    },
    {
        arabic: "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
        translation: "It is You we worship and You we ask for help",
        surah: "Al-Fatiha",
        ayah: "1:5"
    },
    {
        arabic: "ذَٰلِكَ الْكِتَابُ لَا رَيْبَ فِيهِ هُدًى لِلْمُتَّقِينَ",
        translation: "This is the Book about which there is no doubt, a guidance for those conscious of Allah",
        surah: "Al-Baqarah",
        ayah: "2:2"
    },
    {
        arabic: "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
        translation: "Allah - there is no deity except Him, the Ever-Living, the Sustainer of existence",
        surah: "Al-Baqarah",
        ayah: "2:255"
    },
    {
        arabic: "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ",
        translation: "Our Lord, give us in this world good and in the Hereafter good and protect us from the punishment of the Fire",
        surah: "Al-Baqarah",
        ayah: "2:201"
    },
    {
        arabic: "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا",
        translation: "Allah does not burden a soul beyond that it can bear",
        surah: "Al-Baqarah",
        ayah: "2:286"
    },
    {
        arabic: "وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ",
        translation: "And whoever relies upon Allah - then He is sufficient for him",
        surah: "At-Talaq",
        ayah: "65:3"
    },
    {
        arabic: "إِنَّ مَعَ الْعُسْرِ يُسْرًا",
        translation: "Indeed, with hardship comes ease",
        surah: "Ash-Sharh",
        ayah: "94:6"
    },
    {
        arabic: "فَاذْكُرُونِي أَذْكُرْكُمْ وَاشْكُرُوا لِي وَلَا تَكْفُرُونِ",
        translation: "So remember Me; I will remember you. And be grateful to Me and do not deny Me",
        surah: "Al-Baqarah",
        ayah: "2:152"
    },
    {
        arabic: "وَلَسَوْفَ يُعْطِيكَ رَبُّكَ فَتَرْضَىٰ",
        translation: "And your Lord is going to give you, and you will be satisfied",
        surah: "Ad-Duha",
        ayah: "93:5"
    },
    {
        arabic: "قُلْ هُوَ اللَّهُ أَحَدٌ",
        translation: "Say: He is Allah, the One",
        surah: "Al-Ikhlas",
        ayah: "112:1"
    },
    {
        arabic: "وَإِذَا سَأَلَكَ عِبَادِي عَنِّي فَإِنِّي قَرِيبٌ",
        translation: "And when My servants ask you concerning Me - indeed I am near",
        surah: "Al-Baqarah",
        ayah: "2:186"
    },
    {
        arabic: "إِنَّ اللَّهَ مَعَ الصَّابِرِينَ",
        translation: "Indeed, Allah is with the patient",
        surah: "Al-Baqarah",
        ayah: "2:153"
    },
    {
        arabic: "رَبِّ اشْرَحْ لِي صَدْرِي وَيَسِّرْ لِي أَمْرِي",
        translation: "My Lord, expand for me my breast and ease for me my task",
        surah: "Ta-Ha",
        ayah: "20:25-26"
    },
    {
        arabic: "وَنُنَزِّلُ مِنَ الْقُرْآنِ مَا هُوَ شِفَاءٌ وَرَحْمَةٌ لِلْمُؤْمِنِينَ",
        translation: "And We send down of the Quran that which is healing and mercy for the believers",
        surah: "Al-Isra",
        ayah: "17:82"
    },
    {
        arabic: "فَبِأَيِّ آلَاءِ رَبِّكُمَا تُكَذِّبَانِ",
        translation: "So which of the favors of your Lord would you deny?",
        surah: "Ar-Rahman",
        ayah: "55:13"
    },
    {
        arabic: "وَقُل رَّبِّ زِدْنِي عِلْمًا",
        translation: "And say: My Lord, increase me in knowledge",
        surah: "Ta-Ha",
        ayah: "20:114"
    },
    {
        arabic: "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ",
        translation: "Sufficient for us is Allah, and He is the best Disposer of affairs",
        surah: "Ali 'Imran",
        ayah: "3:173"
    },
    {
        arabic: "وَلَا تَيْأَسُوا مِن رَّوْحِ اللَّهِ",
        translation: "And do not despair of relief from Allah",
        surah: "Yusuf",
        ayah: "12:87"
    },
    {
        arabic: "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
        translation: "Verily, in the remembrance of Allah do hearts find rest",
        surah: "Ar-Ra'd",
        ayah: "13:28"
    },
    {
        arabic: "رَبَّنَا لَا تُزِغْ قُلُوبَنَا بَعْدَ إِذْ هَدَيْتَنَا وَهَبْ لَنَا مِن لَّدُنكَ رَحْمَةً",
        translation: "Our Lord, let not our hearts deviate after You have guided us and grant us from Yourself mercy",
        surah: "Ali 'Imran",
        ayah: "3:8"
    },
    {
        arabic: "وَاصْبِرْ فَإِنَّ اللَّهَ لَا يُضِيعُ أَجْرَ الْمُحْسِنِينَ",
        translation: "And be patient, for indeed, Allah does not allow to be lost the reward of those who do good",
        surah: "Hud",
        ayah: "11:115"
    },
    {
        arabic: "إِنَّا فَتَحْنَا لَكَ فَتْحًا مُّبِينًا",
        translation: "Indeed, We have given you a clear conquest",
        surah: "Al-Fath",
        ayah: "48:1"
    },
    {
        arabic: "يَا أَيُّهَا الَّذِينَ آمَنُوا اسْتَعِينُوا بِالصَّبْرِ وَالصَّلَاةِ",
        translation: "O you who have believed, seek help through patience and prayer",
        surah: "Al-Baqarah",
        ayah: "2:153"
    },
    {
        arabic: "وَاللَّهُ يُحِبُّ الصَّابِرِينَ",
        translation: "And Allah loves the steadfast",
        surah: "Ali 'Imran",
        ayah: "3:146"
    },
    {
        arabic: "وَلَنَبْلُوَنَّكُم بِشَيْءٍ مِّنَ الْخَوْفِ وَالْجُوعِ",
        translation: "And We will surely test you with something of fear and hunger",
        surah: "Al-Baqarah",
        ayah: "2:155"
    },
    {
        arabic: "رَبَّنَا تَقَبَّلْ مِنَّا إِنَّكَ أَنتَ السَّمِيعُ الْعَلِيمُ",
        translation: "Our Lord, accept from us. Indeed You are the Hearing, the Knowing",
        surah: "Al-Baqarah",
        ayah: "2:127"
    },
    {
        arabic: "وَمَا تَوْفِيقِي إِلَّا بِاللَّهِ عَلَيْهِ تَوَكَّلْتُ وَإِلَيْهِ أُنِيبُ",
        translation: "And my success is not but through Allah. Upon Him I have relied, and to Him I return",
        surah: "Hud",
        ayah: "11:88"
    }
];

/**
 * Get today's verse based on the day of year.
 * This ensures a different verse each day, cycling through the collection.
 */
function getDailyVerse() {
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

    return JSON.stringify({
        arabic: verse.arabic,
        translation: verse.translation,
        surah: verse.surah,
        ayah: verse.ayah,
        reference: verse.surah + ' ' + verse.ayah,
        date: dateStr,
        dayOfYear: dayOfYear,
        totalVerses: verses.length
    });
}

// Execute and return result
getDailyVerse();
