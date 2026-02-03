export var INUMBER = "INUMBER";
export var IOP1 = "IOP1";
export var IOP2 = "IOP2";
export var IOP3 = "IOP3";
export var IVAR = "IVAR";
export var IVARNAME = "IVARNAME";
export var IFUNCALL = "IFUNCALL";
export var IFUNDEF = "IFUNDEF";
export var IEXPR = "IEXPR";
export var IEXPREVAL = "IEXPREVAL";
export var IMEMBER = "IMEMBER";
export var IENDSTATEMENT = "IENDSTATEMENT";
export var IARRAY = "IARRAY";

/** @typedef {string | number | any[] | null | undefined} InstructionValue */

/**
 * @param {string} type
 * @param {InstructionValue} [value]
 */
export function Instruction(type, value) {
  this.type = type;
  this.value = value !== undefined && value !== null ? value : 0;
}

Instruction.prototype.toString = function () {
  switch (this.type) {
    case INUMBER:
    case IOP1:
    case IOP2:
    case IOP3:
    case IVAR:
    case IVARNAME:
    case IENDSTATEMENT:
      return this.value;
    case IFUNCALL:
      return "CALL " + this.value;
    case IFUNDEF:
      return "DEF " + this.value;
    case IARRAY:
      return "ARRAY " + this.value;
    case IMEMBER:
      return "." + this.value;
    default:
      return "Invalid Instruction";
  }
};

/**
 * @param {InstructionValue} value
 */
export function unaryInstruction(value) {
  return new Instruction(IOP1, value);
}

/**
 * @param {InstructionValue} value
 */
export function binaryInstruction(value) {
  return new Instruction(IOP2, value);
}

/**
 * @param {InstructionValue} value
 */
export function ternaryInstruction(value) {
  return new Instruction(IOP3, value);
}
