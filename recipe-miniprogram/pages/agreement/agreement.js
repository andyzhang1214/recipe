const theme = require('../../utils/theme');

Page({
  data: {
    theme: theme.get(),
    content: ''
  },

  onShow() {
    this.setData({ theme: theme.get() });
  }
});
