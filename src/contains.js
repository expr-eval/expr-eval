/**
 * @param {Array<unknown>} array
 * @param {object} obj
 * @returns {boolean}
 */
export default function contains(array, obj) {
  for (var i = 0; i < array.length; i++) {
    if (array[i] === obj) {
      return true;
    }
  }
  return false;
}
