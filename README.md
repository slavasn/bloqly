# Bloqly

JavaScript Smart Contracts Engine on PBFT blockchain. This is a work in progress/prototype.

[![Build Status](https://travis-ci.org/slavasn/bloqly.svg?branch=master)](https://travis-ci.org/slavasn/bloqly)

## Consensus engine prototype, functional smart contracts

Smart contract code sample:

```JavaScript

const maxSupply = new BigInteger('1000000');
const validatorAmount = new BigInteger('1');
const validatorPower = validatorAmount;

const zero = new BigInteger('0');

/**
 * Move balance contract
 *
 * Simplified implementation of a cryptocurrency  defining  functional  smart contract
 *
 * @param {Object}      ctx        Execution context
 * @param {BigInteger}  amount     Amount to move
 *
 * @return {Object}  Array of properties to set after function execution
 */

function contract(context, amount) {

    var callerBalance = getProperty('caller', 'balance', zero);
    var calleeBalance = getProperty('callee', 'balance', zero);

    var newCallerBalance = callerBalance.subtract(amount);
    var newCalleeBalance = calleeBalance.add(amount);

    return [
        { target: 'caller', balance: newCallerBalance },
        { target: 'callee', balance: newCalleeBalance },
    ];
}

function getPower(target) {
    return getProperty(target, 'balance', zero);
}

function init(context, genesis) {

    return [
        { target: 'caller', balance: new BigInteger('999997') },

        // target = validator's id
        { target: genesis.validators[0].id, balance: validatorAmount },
        { target: genesis.validators[1].id, balance: validatorAmount },
        { target: genesis.validators[2].id, balance: validatorAmount },

        // quorum requirement for active validators group
        { target: 'self', quorum: 2 },

        // active validators group size
        { target: 'self', validators: 3 }

    ];
}

```

[Chat](https://riot.im/app/#/room/#bloqly:matrix.org)
[Twitter](https://twitter.com/slava_snezhkov)
