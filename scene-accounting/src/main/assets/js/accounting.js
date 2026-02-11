/**
 * Smart Accounting — Self-contained HTML/CSS/JS App
 * Runs in engine WebView, builds complete HTML, calls window.show() to display popup.
 * All interactive logic runs inside the popup WebView via Function.toString() injection.
 */

function buildApp() {

    // ============ Data & Rules ============

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

    var incomeKeywords = ['工资', '薪水', '奖金', '收入', '到账', '进账', '赚',
                          '报销', '退款', '利息', '分红', '兼职', '稿费',
                          '转入', '收到', '红包收入'];

    var STORAGE_KEY = 'accounting_transactions';

    // ============ Storage ============

    function loadTransactions() {
        try {
            var raw = localStorage.getItem(STORAGE_KEY);
            return raw ? JSON.parse(raw) : [];
        } catch (e) {
            return [];
        }
    }

    function saveTransactions(list) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
    }

    // ============ Parsing Logic ============

    function extractAmount(input) {
        var chineseNums = {
            '零': 0, '一': 1, '二': 2, '两': 2, '三': 3, '四': 4,
            '五': 5, '六': 6, '七': 7, '八': 8, '九': 9, '十': 10,
            '百': 100, '千': 1000, '万': 10000
        };
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
                if (!isNaN(num) && num > 0) return num;
            }
        }
        var chinesePattern = /([零一二两三四五六七八九十百千万]+)[块元圆]?/;
        var chineseMatch = input.match(chinesePattern);
        if (chineseMatch) {
            var converted = convertChineseNumber(chineseMatch[1], chineseNums);
            if (converted > 0) return converted;
        }
        return null;
    }

    function convertChineseNumber(str, mapping) {
        if (!str) return 0;
        var result = 0, current = 0;
        for (var i = 0; i < str.length; i++) {
            var val_ = mapping[str[i]];
            if (val_ === undefined) continue;
            if (val_ >= 10) {
                if (current === 0) current = 1;
                current *= val_;
                if (val_ >= 10000 || i === str.length - 1) { result += current; current = 0; }
            } else {
                current = val_;
            }
        }
        return result + current;
    }

    function determineType(input) {
        for (var i = 0; i < incomeKeywords.length; i++) {
            if (input.indexOf(incomeKeywords[i]) !== -1) return 'income';
        }
        return 'expense';
    }

    function determineCategory(input) {
        var maxScore = 0, bestCategory = '其他';
        var categories = Object.keys(categoryRules);
        for (var i = 0; i < categories.length; i++) {
            var cat = categories[i], keywords = categoryRules[cat], score = 0;
            for (var j = 0; j < keywords.length; j++) {
                if (input.indexOf(keywords[j]) !== -1) score++;
            }
            if (score > maxScore) { maxScore = score; bestCategory = cat; }
        }
        return bestCategory;
    }

    function generateDescription(input) {
        var desc = input
            .replace(/\d+\.?\d*\s*[块元圆角分钱]/g, '')
            .replace(/花[了过去]?\s*\d+\.?\d*/g, '')
            .replace(/[了过去]\s*\d+\.?\d*/g, '')
            .trim();
        if (desc.length === 0) return determineCategory(input);
        if (desc.length > 50) desc = desc.substring(0, 50) + '...';
        return desc;
    }

    function parseTransaction(input) {
        if (!input || input.trim() === '') return { success: false, error: '请输入记账内容' };
        input = input.trim();
        var amount = extractAmount(input);
        if (amount === null) return { success: false, error: '无法识别金额' };
        return {
            success: true, amount: amount,
            category: determineCategory(input), type: determineType(input),
            description: generateDescription(input), rawInput: input
        };
    }

    // ============ XSS Prevention ============

    function escapeHtml(str) {
        if (!str) return '';
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // ============ UI ============

    function injectStyles() {
        var s = document.createElement('style');
        s.textContent =
            '* { margin:0; padding:0; box-sizing:border-box; }' +
            'body { font-family:-apple-system,"Helvetica Neue",sans-serif; background:#F5F5F5; color:#212121; -webkit-tap-highlight-color:transparent; }' +
            '.header { background:#283593; color:#fff; padding:20px 16px 16px; }' +
            '.header h1 { font-size:20px; font-weight:500; }' +
            '.input-area { background:#fff; padding:12px 16px; display:flex; gap:8px; box-shadow:0 1px 3px rgba(0,0,0,0.1); }' +
            '.input-area input { flex:1; border:1px solid #E0E0E0; border-radius:8px; padding:10px 12px; font-size:15px; outline:none; }' +
            '.input-area input:focus { border-color:#283593; }' +
            '.input-area button { background:#283593; color:#fff; border:none; border-radius:8px; padding:10px 20px; font-size:15px; white-space:nowrap; }' +
            '.input-area button:active { background:#1A237E; }' +
            '.result-msg { padding:8px 16px; font-size:14px; display:none; }' +
            '.result-msg.success { color:#2E7D32; background:#E8F5E9; }' +
            '.result-msg.error { color:#C62828; background:#FFEBEE; }' +
            '.summary { display:flex; gap:12px; padding:12px 16px; }' +
            '.summary-card { flex:1; background:#fff; border-radius:12px; padding:14px; box-shadow:0 1px 3px rgba(0,0,0,0.08); text-align:center; }' +
            '.summary-card .label { font-size:12px; color:#757575; margin-bottom:4px; }' +
            '.summary-card .value { font-size:20px; font-weight:600; }' +
            '.summary-card .value.expense { color:#F44336; }' +
            '.summary-card .value.income { color:#4CAF50; }' +
            '.section-title { padding:12px 16px 8px; font-size:13px; color:#757575; }' +
            '.tx-list { padding:0 16px 80px; }' +
            '.tx-item { background:#fff; border-radius:12px; padding:14px; margin-bottom:8px; box-shadow:0 1px 2px rgba(0,0,0,0.06); display:flex; align-items:center; gap:12px; }' +
            '.tx-tag { font-size:11px; color:#fff; padding:2px 8px; border-radius:10px; white-space:nowrap; }' +
            '.tx-body { flex:1; min-width:0; }' +
            '.tx-title { font-size:15px; color:#212121; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }' +
            '.tx-sub { font-size:12px; color:#9E9E9E; margin-top:2px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }' +
            '.tx-right { text-align:right; flex-shrink:0; }' +
            '.tx-amount { font-size:16px; font-weight:600; }' +
            '.tx-time { font-size:11px; color:#BDBDBD; margin-top:2px; }' +
            '.tx-del { background:none; border:none; color:#BDBDBD; font-size:18px; padding:4px 8px; }' +
            '.tx-del:active { color:#F44336; }' +
            '.empty { text-align:center; color:#BDBDBD; padding:40px 16px; font-size:15px; }';
        document.head.appendChild(s);
    }

    function renderLayout() {
        document.body.innerHTML =
            '<div class="header"><h1>\u667A\u80FD\u8BB0\u8D26</h1></div>' +
            '<div class="input-area">' +
            '<input id="inp" type="text" placeholder="\u8F93\u5165\u8BB0\u8D26\u5185\u5BB9\uFF0C\u5982\uFF1A\u5348\u996D\u82B1\u4E8630\u5757">' +
            '<button id="btn">\u8BB0\u8D26</button></div>' +
            '<div id="msg" class="result-msg"></div>' +
            '<div class="summary">' +
            '<div class="summary-card"><div class="label">\u603B\u652F\u51FA</div><div id="sum-expense" class="value expense">\u00a50.00</div></div>' +
            '<div class="summary-card"><div class="label">\u603B\u6536\u5165</div><div id="sum-income" class="value income">\u00a50.00</div></div>' +
            '</div>' +
            '<div class="section-title">\u4EA4\u6613\u8BB0\u5F55</div>' +
            '<div id="list" class="tx-list"></div>';
    }

    function bindEvents() {
        document.getElementById('btn').addEventListener('click', function () {
            handleInput(document.getElementById('inp').value);
        });
        document.getElementById('inp').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') handleInput(this.value);
        });
    }

    function handleInput(text) {
        var result = parseTransaction(text);
        var msgEl = document.getElementById('msg');
        if (result.success) {
            var transactions = loadTransactions();
            transactions.unshift({
                id: Date.now(), amount: result.amount, category: result.category,
                description: result.description, type: result.type,
                rawInput: result.rawInput, timestamp: Date.now()
            });
            saveTransactions(transactions);
            var typeText = result.type === 'expense' ? '\u652F\u51FA' : '\u6536\u5165';
            msgEl.textContent = '\u2713 ' + typeText + ' \u00a5' + result.amount.toFixed(2) + ' | ' + result.category + ' | ' + result.description;
            msgEl.className = 'result-msg success';
            msgEl.style.display = 'block';
            document.getElementById('inp').value = '';
            refreshUI();
        } else {
            msgEl.textContent = '\u2717 ' + result.error;
            msgEl.className = 'result-msg error';
            msgEl.style.display = 'block';
        }
        setTimeout(function () { msgEl.style.display = 'none'; }, 3000);
    }

    function deleteTransaction(id) {
        var transactions = loadTransactions().filter(function (t) { return t.id !== id; });
        saveTransactions(transactions);
        refreshUI();
    }

    function refreshUI() {
        var transactions = loadTransactions();
        var totalExpense = 0, totalIncome = 0, html = '';
        if (transactions.length === 0) {
            html = '<div class="empty">\u6682\u65E0\u4EA4\u6613\u8BB0\u5F55</div>';
        } else {
            for (var i = 0; i < transactions.length; i++) {
                var t = transactions[i];
                var amount = parseFloat(t.amount) || 0;
                var isExpense = t.type === 'expense';
                if (isExpense) { totalExpense += amount; } else { totalIncome += amount; }
                var prefix = isExpense ? '-\u00a5' : '+\u00a5';
                var color = isExpense ? '#F44336' : '#4CAF50';
                var tagColor = categoryColors[t.category] || '#78909C';
                var d = new Date(t.timestamp);
                var timeStr = (d.getMonth() + 1) + '/' + d.getDate() + ' ' +
                    ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
                html += '<div class="tx-item">' +
                    '<span class="tx-tag" style="background:' + tagColor + '">' + escapeHtml(t.category) + '</span>' +
                    '<div class="tx-body">' +
                    '<div class="tx-title">' + escapeHtml(t.description) + '</div>' +
                    '<div class="tx-sub">\u300C' + escapeHtml(t.rawInput) + '\u300D</div></div>' +
                    '<div class="tx-right">' +
                    '<div class="tx-amount" style="color:' + color + '">' + prefix + amount.toFixed(2) + '</div>' +
                    '<div class="tx-time">' + escapeHtml(timeStr) + '</div></div>' +
                    '<button class="tx-del" data-id="' + t.id + '">\u00d7</button></div>';
            }
        }
        document.getElementById('list').innerHTML = html;
        document.getElementById('sum-expense').textContent = '\u00a5' + totalExpense.toFixed(2);
        document.getElementById('sum-income').textContent = '\u00a5' + totalIncome.toFixed(2);
        var delBtns = document.querySelectorAll('.tx-del');
        for (var j = 0; j < delBtns.length; j++) {
            delBtns[j].addEventListener('click', function () {
                deleteTransaction(parseInt(this.getAttribute('data-id')));
            });
        }
    }

    // ============ Init ============

    injectStyles();
    renderLayout();
    bindEvents();
    refreshUI();
}

// === Engine entry: build HTML shell, inject app via Function.toString(), show popup ===

var html = '<!DOCTYPE html><html><head>' +
    '<meta charset="UTF-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">' +
    '</head><body>' +
    '<script>(' + buildApp.toString() + ')()<\/script>' +
    '</body></html>';

__bridge.invoke('window', 'show', JSON.stringify({ html: html }));
