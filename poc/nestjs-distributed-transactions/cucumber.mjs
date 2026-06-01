export default {
  default: {
    paths: ['tests/atdd/**/*.feature'],
    require: ['tests/atdd/steps/**/*.js'],
    format: ['progress'],
    publishQuiet: true,
  },
};
