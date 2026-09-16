const theme = require('../../utils/theme');

Page({
  data: {
    theme: theme.get()
  },

  onShow() {
    this.setData({ theme: theme.get() });
  }
});
