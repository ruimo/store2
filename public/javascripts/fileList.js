$(function() {
  var clip = new Clipboard('.copyBtn');
  $("button,input[type='button'],input[type='submit']").button();
  
  $(".removeBtn").button({
    icons: {
      primary: 'ui-icon-trash'
    },
    text: false
  });

  $(".removeBtn").click(function(e) {
    if (confirm($(this).attr("data-confirm"))) {
      var id = $(this).attr("data-id");
      $.ajax({
        type: 'post',
        url: $("#removeForm").attr("action"),
        data: JSON.stringify({pathId: id}),
        contentType: 'application/json',
        dataType: 'json',
        cache: false,
        success: function(data, status, jqXhr) {
          if (data["status"] == "ok") {
            location.reload();
          }
          else {
            alert("ただいま混雑しています。少し時間をあけて試してください。");
          }
        },
        error: function(jqXhr, status, error) {
          if (jqXhr.responseJSON.status == "notfound") {
            alert("指定されたファイルは見つかりませんでした。");
            location.reload();
          }
          else {
            alert("ただいま混雑しています。少し時間をあけて試してください。");
          }
        }
      });
    }
  });

  $(".removeDirBtn").button({
    icons: {
      primary: 'ui-icon-trash'
    },
    text: false
  });

  $(".removeDirBtn").click(function(e) {
    if (confirm($(this).attr("data-confirm"))) {
      var id = $(this).attr("data-id");

      $.ajax({
        type: 'post',
        url: $("#removeDirForm").attr("action"),
        data: JSON.stringify({pathId: id}),
        contentType: 'application/json',
        dataType: 'json',
        cache: false,
        success: function(data, status, jqXhr) {
          if (data["status"] == "ok") {
            location.reload();
          }
          else {
            alert("ただいま混雑しています。少し時間をあけて試してください。");
          }
        },
        error: function(jqXhr, status, error) {
          if (jqXhr.responseJSON.status == "notfound") {
            alert("指定されたディレクトリは見つかりませんでした。");
            location.reload();
          }
          else if (jqXhr.responseJSON.status == "forbidden") {
            alert("許されない操作です。");
          }
          else if (jqXhr.responseJSON.status == "notempty") {
            alert("ディレクトリが空ではありません。");
          }
          else {
            alert("ただいま混雑しています。少し時間をあけて試してください。");
          }
        }
      });
    }
  });

  $("#createDirBtn").click(function(e) {
    var pathValue = $("#path").val().trim();
    if (pathValue == "") {
      alert("ディレクトリ名を入力してください。");
    }
    else {
      $.ajax({
        type: 'post',
        url: $("#createDirForm").attr("action"),
        data: JSON.stringify({path: pathValue}),
        contentType: 'application/json',
        dataType: 'json',
        cache: false,
        success: function(data, status, jqXhr) {
          if (data["status"] == "ok") {
            location.reload();
          }
          else {
            alert("ただいま混雑しています。少し時間をあけて試してください。");
          }
        },
        error: function(jqXhr, status, error) {
          if (jqXhr.responseJSON.status == "duplicated") {
            alert("同名ディレクトリが既に存在します。");
          }
          else if (jqXhr.responseJSON.status == "forbidden") {
            alert("許されない操作です。");
          }
          else {
            alert("ただいま混雑しています。少し時間をあけて試してください。");
          }
        }
      });
    }
  });
});
