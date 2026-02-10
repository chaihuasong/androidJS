/**
 * Smart Accounting JS Logic
 * Parses natural language input to extract transaction data.
 * Supports Chinese language input for amount, category, and type detection.
 * Uses generic database and ui modules for storage and display.
 */

// Category rules mapping keywords to categories
var categoryRules = {
    '餐饮': ['吃', '饭', '餐', '午饭', '晚饭', '早饭', '外卖', '火锅', '烧烤',
             '奶茶', '咖啡', '饮料', '面', '粥', '食', '零食', '小吃', '甜品',
             '蛋糕', '水果', '菜', '肉', '鱼', '虾', '鸡', '猪', '牛',
             '食堂', '餐厅', '饭店', '美团', '饿了么', '麦当劳', '肯德基',
             '星巴克', '瑞幸', '喝'],
    '交通': ['打车', '出租', '滴滴', '公交', '地铁', '高铁', '火车', '飞机',
             '机票', '车票', '加油', '停车', '过路费', '汽油', '油费', '出行',
             '骑车', '单车', '共享', 'uber', '高速'],
    '购物': ['买', '购', '淘宝', '京东', '拼多多', '商场', '超市', '衣服',
             '鞋', '包', '化妆品', '日用品', '生活用品', '电器', '手机',
             '电脑', '数码'],
    '娱乐': ['电影', '游戏', '唱歌', 'KTV', '演出', '门票', '旅游', '旅行',
             '景点', '酒店', '住宿', '民宿', '玩', '健身', '运动', '游泳',
             '球', '会员', 'VIP', '视频'],
    '住房': ['房租', '租金', '水电', '电费', '水费', '燃气', '物业', '装修',
             '家具', '房贷', '宽带', '网费'],
    '医疗': ['医院', '看病', '药', '挂号', '体检', '牙', '眼', '保险',
             '医疗', '门诊', '住院'],
    '教育': ['书', '学', '课', '培训', '教材', '文具', '考试', '学费',
             '补习', '辅导'],
    '通讯': ['话费', '流量', '充值', '手机费', '电话'],
    '人情': ['红包', '礼物', '礼金', '份子', '请客', '送礼', '转账'],
    '其他': []
};

var categoryColors = {
    '餐饮': '#FF7043', '交通': '#42A5F5', '购物': '#AB47BC',
    '娱乐': '#FFA726', '住房': '#26A69A', '医疗': '#EF5350',
    '教育': '#5C6BC0', '通讯': '#66BB6A', '人情': '#EC407A',
    '其他': '#78909C'
};

// Income keywords
var incomeKeywords = ['工资', '薪水', '奖金', '收入', '到账', '进账', '赚',
                      '报销', '退款', '利息', '分红', '兼职', '稿费',
                      '转入', '收到', '红包收入'];

/**
 * Called by ScriptActivity when the activity is created.
 */
function onActivityCreated() {
    __bridge.invoke('ui', 'configure', JSON.stringify({
        title: '智能记账',
        inputHint: '输入记账内容，如：午饭花了30块',
        submitText: '记账',
        showVoice: true,
        voiceLocale: 'zh-CN',
        summaryLabels: ['总支出', '总收入'],
        summaryColors: ['#F44336', '#4CAF50']
    }));

    // Create table if not exists
    __bridge.invoke('database', 'exec', JSON.stringify({
        sql: 'CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL, category TEXT, description TEXT, type TEXT, rawInput TEXT, timestamp INTEGER)'
    }));

    refreshList();
}

/**
 * Called when the user submits text input.
 */
function onInput(text) {
    var result = JSON.parse(parseTransaction(text));
    if (result.success) {
        __bridge.invoke('database', 'exec', JSON.stringify({
            sql: 'INSERT INTO transactions (amount, category, description, type, rawInput, timestamp) VALUES (?, ?, ?, ?, ?, ?)',
            params: [result.amount, result.category, result.description, result.type, result.rawInput, Date.now()]
        }));

        var typeText = result.type === 'expense' ? '支出' : '收入';
        __bridge.invoke('ui', 'showResult', JSON.stringify({
            text: '\u2713 ' + typeText + ' \u00a5' + result.amount.toFixed(2) + ' | 分类: ' + result.category + ' | ' + result.description,
            success: true
        }));
        __bridge.invoke('ui', 'clearInput', '{}');
        refreshList();
    } else {
        __bridge.invoke('ui', 'showResult', JSON.stringify({
            text: '\u2717 解析失败: ' + result.error,
            success: false
        }));
    }
}

/**
 * Called when voice recognition returns text.
 */
function onVoiceResult(text) {
    onInput(text);
}

/**
 * Refresh the transaction list and summary from database.
 */
function refreshList() {
    var queryResult = JSON.parse(__bridge.invoke('database', 'query', JSON.stringify({
        sql: 'SELECT * FROM transactions ORDER BY timestamp DESC'
    })));

    // Parse bridge response — result may be wrapped in {success, data}
    var data = queryResult;
    if (queryResult.success !== undefined && queryResult.data) {
        data = typeof queryResult.data === 'string' ? JSON.parse(queryResult.data) : queryResult.data;
    }

    var rows = data.rows || [];
    var items = [];
    var totalExpense = 0;
    var totalIncome = 0;

    for (var i = 0; i < rows.length; i++) {
        var row = rows[i];
        var amount = parseFloat(row.amount) || 0;
        var type = row.type || 'expense';
        var category = row.category || '其他';
        var isExpense = type === 'expense';

        if (isExpense) {
            totalExpense += amount;
        } else {
            totalIncome += amount;
        }

        var prefix = isExpense ? '-\u00a5' : '+\u00a5';
        var ts = parseInt(row.timestamp) || 0;
        var d = new Date(ts);
        var timeStr = (d.getMonth() + 1) + '/' + d.getDate() + ' ' +
            ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);

        items.push({
            tag: category,
            tagColor: categoryColors[category] || '#78909C',
            title: row.description || category,
            subtitle: '\u300c' + (row.rawInput || '') + '\u300d',
            value: prefix + amount.toFixed(2),
            valueColor: isExpense ? '#F44336' : '#4CAF50',
            time: timeStr
        });
    }

    __bridge.invoke('ui', 'updateList', JSON.stringify({ items: items }));
    __bridge.invoke('ui', 'updateSummary', JSON.stringify({
        values: ['\u00a5' + totalExpense.toFixed(2), '\u00a5' + totalIncome.toFixed(2)]
    }));
}

/**
 * Parse a natural language input string to extract transaction data.
 * @param {string} input - User input text (e.g., "午饭花了30块")
 * @returns {string} JSON string with parsed transaction data
 */
function parseTransaction(input) {
    if (!input || input.trim() === '') {
        return JSON.stringify({
            success: false,
            error: 'Empty input'
        });
    }

    input = input.trim();

    // Extract amount
    var amount = extractAmount(input);
    if (amount === null) {
        return JSON.stringify({
            success: false,
            error: 'Could not extract amount from input',
            rawInput: input
        });
    }

    // Determine type (expense or income)
    var type = determineType(input);

    // Determine category
    var category = determineCategory(input);

    // Extract description
    var description = generateDescription(input, amount, category);

    return JSON.stringify({
        success: true,
        amount: amount,
        category: category,
        type: type,
        description: description,
        rawInput: input
    });
}

/**
 * Extract numeric amount from input text.
 */
function extractAmount(input) {
    // Chinese number mappings
    var chineseNums = {
        '零': 0, '一': 1, '二': 2, '两': 2, '三': 3, '四': 4,
        '五': 5, '六': 6, '七': 7, '八': 8, '九': 9, '十': 10,
        '百': 100, '千': 1000, '万': 10000
    };

    // Try Arabic numerals first (e.g., "30", "30.5", "30块", "30元")
    var patterns = [
        /(\d+\.?\d*)\s*[块元圆角分钱]/,
        /花[了过去]?\s*(\d+\.?\d*)/,
        /[了过去]\s*(\d+\.?\d*)/,
        /(\d+\.?\d*)/
    ];

    for (var i = 0; i < patterns.length; i++) {
        var match = input.match(patterns[i]);
        if (match) {
            var num = parseFloat(match[1]);
            if (!isNaN(num) && num > 0) {
                return num;
            }
        }
    }

    // Try simple Chinese numbers (e.g., "三十", "一百")
    var chinesePattern = /([零一二两三四五六七八九十百千万]+)[块元圆]?/;
    var chineseMatch = input.match(chinesePattern);
    if (chineseMatch) {
        var converted = convertChineseNumber(chineseMatch[1], chineseNums);
        if (converted > 0) return converted;
    }

    return null;
}

/**
 * Convert Chinese number string to Arabic number.
 */
function convertChineseNumber(str, mapping) {
    if (!str) return 0;

    var result = 0;
    var current = 0;

    for (var i = 0; i < str.length; i++) {
        var char = str[i];
        var val_ = mapping[char];

        if (val_ === undefined) continue;

        if (val_ >= 10) {
            if (current === 0) current = 1;
            current *= val_;
            if (val_ >= 10000 || i === str.length - 1) {
                result += current;
                current = 0;
            }
        } else {
            current = val_;
        }
    }

    result += current;
    return result;
}

/**
 * Determine if the transaction is an expense or income.
 */
function determineType(input) {
    for (var i = 0; i < incomeKeywords.length; i++) {
        if (input.indexOf(incomeKeywords[i]) !== -1) {
            return 'income';
        }
    }
    return 'expense';
}

/**
 * Determine the category based on keyword matching.
 */
function determineCategory(input) {
    var maxScore = 0;
    var bestCategory = '其他';

    var categories = Object.keys(categoryRules);
    for (var i = 0; i < categories.length; i++) {
        var cat = categories[i];
        var keywords = categoryRules[cat];
        var score = 0;

        for (var j = 0; j < keywords.length; j++) {
            if (input.indexOf(keywords[j]) !== -1) {
                score++;
            }
        }

        if (score > maxScore) {
            maxScore = score;
            bestCategory = cat;
        }
    }

    return bestCategory;
}

/**
 * Generate a short description from the input.
 */
function generateDescription(input, amount, category) {
    // Remove amount-related text to get a cleaner description
    var desc = input
        .replace(/\d+\.?\d*\s*[块元圆角分钱]/g, '')
        .replace(/花[了过去]?\s*\d+\.?\d*/g, '')
        .replace(/[了过去]\s*\d+\.?\d*/g, '')
        .trim();

    if (desc.length === 0) {
        return category;
    }

    // Limit description length
    if (desc.length > 50) {
        desc = desc.substring(0, 50) + '...';
    }

    return desc;
}

// Export functions globally
this.parseTransaction = parseTransaction;
this.onActivityCreated = onActivityCreated;
this.onInput = onInput;
this.onVoiceResult = onVoiceResult;

// Return a confirmation that the script loaded
JSON.stringify({ loaded: true, module: 'accounting' });
